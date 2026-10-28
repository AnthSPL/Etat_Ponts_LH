package fr.anthspl.etatpontlehavre.notifications

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import fr.anthspl.etatpontlehavre.data.model.BridgeCoordinates
import fr.anthspl.etatpontlehavre.data.model.BridgeStatus
import fr.anthspl.etatpontlehavre.data.remote.SWingDataSource
import kotlinx.coroutines.CancellationException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class BridgeAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val prefs = context.getSharedPreferences("bridge_alert_prefs", Context.MODE_PRIVATE)
    private val favPrefs = context.getSharedPreferences("bridge_favorites_prefs", Context.MODE_PRIVATE)

    override suspend fun doWork(): Result {
        val interval = getInterval(applicationContext)
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.FRANCE).format(Date())

        Log.i("BridgeAlertWorker", "🔄 [$timeStr] DÉBUT de la vérification S-WiNG (Fréquence réglée : $interval min)")

        try {
            checkBridgesAndNotify()
        } catch (e: CancellationException) {
            Log.i("BridgeAlertWorker", "ℹ️ Tâche interrompue / annulée proprement.")
            throw e
        } catch (e: Exception) {
            Log.e("BridgeAlertWorker", "❌ Erreur dans doWork()", e)
            return Result.failure()
        }

        // Reprogramme la prochaine vérification sans interrompre le worker courant
        scheduleNextCheck(applicationContext, policy = ExistingWorkPolicy.APPEND_OR_REPLACE)

        return Result.success()
    }

    @SuppressLint("MissingPermission")
    private suspend fun checkBridgesAndNotify() {
        try {
            val dataSource = SWingDataSource()
            val bridges = dataSource.fetchLeHavreBridges()
            val timeStr = SimpleDateFormat("HH:mm:ss", Locale.FRANCE).format(Date())

            Log.i("BridgeAlertWorker", "✅ [$timeStr] Données Haropa Port / S-WiNG récupérées avec succès.")

            val favoriteIds = favPrefs.getStringSet("favorite_bridge_ids", emptySet()) ?: emptySet()
            val legacyFavorites = prefs.getStringSet("favorite_bridges", emptySet()) ?: emptySet()

            val helper = BridgeNotificationHelper(applicationContext)
            val locationManager = applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            // Récupération de la préférence utilisateur (Par défaut : false -> on respecte la règle des 15 min)
            val bypassGpsCheck = prefs.getBoolean("bypass_gps_check", false)

            // 1. Récupération de la dernière position connue (GPS ou Réseau) depuis le cache système
            val gpsLoc = if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            } else null

            val netLoc = if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            } else null

            var bestLocation: Location? = null
            listOfNotNull(gpsLoc, netLoc).forEach { loc ->
                if (bestLocation == null || loc.time > bestLocation!!.time) {
                    bestLocation = loc
                }
            }

            // 2. Gestion de l'âge de la position en cache (Max 15 minutes)
            if (bestLocation != null) {
                val ageMinutes = (System.currentTimeMillis() - bestLocation!!.time) / (1000 * 60)
                Log.i("BridgeAlertWorker", "📍 Position en cache trouvée : lat=${bestLocation!!.latitude}, lon=${bestLocation!!.longitude} (Âge : $ageMinutes minutes)")

                val maxAgeMillis = 15 * 60 * 1000L // 15 minutes
                if (System.currentTimeMillis() - bestLocation!!.time > maxAgeMillis) {
                    if (bypassGpsCheck) {
                        Log.w("BridgeAlertWorker", "⚠️ Position trop ancienne ($ageMinutes min), mais le mode 'Ignorer l'âge GPS' est activé. On l'utilise quand même.")
                    } else {
                        Log.w("BridgeAlertWorker", "⚠️ Position en cache trop ancienne ($ageMinutes minutes). Ignorée.")
                        bestLocation = null
                    }
                }
            } else {
                Log.w("BridgeAlertWorker", "⚠️ Aucun cache GPS disponible.")
            }

            // 3. Sécurité conditionnelle :
            // Si aucune position n'est dispo ou valide ET que l'utilisateur n'a PAS activé le bypass, on stoppe.
            // Si le bypass est activé, on autorise bestLocation à être null (la distance sera simplement ignorée/considérée comme valide pour les favoris).
            if (bestLocation == null) {
                if (bypassGpsCheck) {
                    Log.i("BridgeAlertWorker", "ℹ️ Aucune position GPS valide, mais le mode 'Toujours notifier' est actif. Passage outre la vérification de distance.")
                } else {
                    Log.w("BridgeAlertWorker", "⚠️ Aucune position GPS valide ou récente. Notification ignorée pour préserver la distance.")
                    return
                }
            } else {
                Log.i("BridgeAlertWorker", "📍 Dernière position connue validée et utilisée.")
            }

            for (bridge in bridges) {
                val bridgeName = bridge.name
                val coords = BridgeCoordinates.map[bridgeName]
                val isClosed = bridge.status == BridgeStatus.CLOSED

                // Vérifie si le pont est dans les favoris (par son ID ou par son Nom)
                val isFavorite = favoriteIds.contains(bridge.id) ||
                        favoriteIds.contains(bridgeName) ||
                        legacyFavorites.contains(bridgeName) ||
                        legacyFavorites.contains(bridge.id)

                val wasClosedKey = "was_closed_$bridgeName"
                val wasPreviouslyClosed = prefs.getBoolean(wasClosedKey, false)
                val notifiedReopenedKey = "notified_reopened_$bridgeName"
                val alreadyNotifiedReopened = prefs.getBoolean(notifiedReopenedKey, false)

                // 4. Calcul strict de la distance (uniquement si on a une position valide, ou ignoré si bypass et position null)
                var distanceInKm: Double? = null
                if (bestLocation != null && coords != null) {
                    val results = FloatArray(1)
                    Location.distanceBetween(
                        bestLocation!!.latitude, bestLocation!!.longitude,
                        coords.first, coords.second,
                        results
                    )
                    distanceInKm = results[0] / 1000.0
                }

                // --- 1. Notification de Réouverture (Fermé -> Ouvert) ---
                if (wasPreviouslyClosed && !isClosed) {
                    if (!alreadyNotifiedReopened) {
                        val distanceConditionMet = bypassGpsCheck || (distanceInKm != null && distanceInKm <= 3.0)
                        if (isFavorite && distanceConditionMet) {
                            Log.i("BridgeAlertWorker", "🔔 Envoi notification Réouverture pour : $bridgeName")
                            helper.sendNowOpenNotification(
                                bridgeName,
                                "Bonne nouvelle : $bridgeName est à nouveau ouvert à la circulation !"
                            )
                            prefs.edit().putBoolean(notifiedReopenedKey, true).apply()
                        }
                    }
                    prefs.edit().putBoolean(wasClosedKey, false).apply()
                }

                // --- 2. Notification de Fermeture (Favori + Fermé) ---
                val distanceConditionMetClosure = bypassGpsCheck || (distanceInKm != null && distanceInKm <= 10.0)
                if (isFavorite && isClosed && distanceConditionMetClosure) {
                    val notifKey = "notified_closed_$bridgeName"
                    if (!prefs.getBoolean(notifKey, false)) {
                        Log.i("BridgeAlertWorker", "🔔 Envoi notification Fermeture pour : $bridgeName")
                        helper.sendClosureAlertNotification(
                            bridgeName,
                            "Attention : $bridgeName est fermé !"
                        )
                        prefs.edit().putBoolean(notifKey, true).apply()
                    }
                    prefs.edit()
                        .putBoolean(wasClosedKey, true)
                        .putBoolean(notifiedReopenedKey, false)
                        .apply()
                } else if (!isClosed) {
                    prefs.edit().remove("notified_closed_$bridgeName").apply()
                }
            }

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("BridgeAlertWorker", "❌ Erreur lors de la vérification des ponts S-WiNG", e)
        }
    }

    companion object {
        private const val WORK_NAME = "BridgeAlertWorkOneTime"
        private const val PREF_CHECK_INTERVAL_MINUTES = "check_interval_minutes"
        private const val DEFAULT_INTERVAL_MINUTES = 5

        fun getInterval(context: Context): Int {
            val prefs = context.getSharedPreferences("bridge_alert_prefs", Context.MODE_PRIVATE)
            return prefs.getInt(PREF_CHECK_INTERVAL_MINUTES, DEFAULT_INTERVAL_MINUTES)
        }

        fun setInterval(context: Context, minutes: Int) {
            val prefs = context.getSharedPreferences("bridge_alert_prefs", Context.MODE_PRIVATE)
            prefs.edit().putInt(PREF_CHECK_INTERVAL_MINUTES, minutes).apply()
            Log.i("BridgeAlertWorker", "⚙️ Changement de fréquence : $minutes minute(s).")
            scheduleNextCheck(context, minutes.toLong(), ExistingWorkPolicy.REPLACE)
        }

        fun scheduleNextCheck(
            context: Context,
            delayMinutes: Long? = null,
            policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP
        ) {
            val interval = delayMinutes ?: getInterval(context).toLong()

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<BridgeAlertWorker>()
                .setConstraints(constraints)
                .setInitialDelay(interval, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                policy,
                workRequest
            )

            Log.i("BridgeAlertWorker", "⏰ Tâche planifiée : prochaine interrogation S-WiNG dans $interval minute(s) [Politique : $policy].")
        }

        fun schedulePeriodicCheck(context: Context) {
            scheduleNextCheck(context, policy = ExistingWorkPolicy.KEEP)
        }
    }
}