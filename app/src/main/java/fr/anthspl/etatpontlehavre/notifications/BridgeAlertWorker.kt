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
import fr.anthspl.etatpontlehavre.data.remote.MeteoFranceApi
import fr.anthspl.etatpontlehavre.data.remote.SWingDataSource
import fr.anthspl.etatpontlehavre.data.repository.VigilanceRepository
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import kotlinx.coroutines.CancellationException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import okhttp3.ConnectionSpec

class BridgeAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val prefs = context.getSharedPreferences("bridge_alert_prefs", Context.MODE_PRIVATE)
    private val favPrefs = context.getSharedPreferences("bridge_favorites_prefs", Context.MODE_PRIVATE)

    override suspend fun doWork(): Result {
        val interval = getInterval(applicationContext)
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.FRANCE).format(Date())

        Log.i("BridgeAlertWorker", "🔄 [$timeStr] DÉBUT de la vérification (S-WiNG + Météo-France)")

        try {
            // 1. Vérification des alertes météo Météo-France (Vent violent / Ponts de Normandie et Tancarville)
            checkMeteoWindAlertAndNotify()

            // 2. Vérification classique des ponts levants (S-WiNG)
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

    private suspend fun checkMeteoWindAlertAndNotify() {
        try {
            // Création d'un client OkHttp enrichi simulant une navigation web légitime
            val okHttpClient = OkHttpClient.Builder()
                .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS))
                .addInterceptor { chain ->
                    val originalRequest = chain.request()
                    val requestWithHeaders = originalRequest.newBuilder()
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                        .header("Accept", "application/json")
                        .header("Connection", "keep-alive")
                        .build()
                    chain.proceed(requestWithHeaders)
                }
                .build()

            val json = Json { ignoreUnknownKeys = true }
            val retrofit = Retrofit.Builder()
                .baseUrl("https://public-api.meteofrance.fr/") // Indispensable : se termine par un /
                .client(okHttpClient)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()

            val api = retrofit.create(MeteoFranceApi::class.java)
            val repository = VigilanceRepository(api)

            val isWindAlertActive = repository.isWindAlertOrangeOrRedForDept76()

            val alertKey = "notified_meteo_wind_alert_76"
            val alreadyNotified = prefs.getBoolean(alertKey, false)
            val helper = BridgeNotificationHelper(applicationContext)

            if (isWindAlertActive) {
                if (!alreadyNotified) {
                    Log.i("BridgeAlertWorker", "⚠️ Alerte Vent Orange/Rouge détectée en Seine-Maritime ! Envoi de la notification.")
                    helper.sendClosureAlertNotification(
                        "Ponts de Normandie & Tancarville",
                        "⚠️ Vigilance Vent Orange/Rouge : Risque élevé de restrictions ou de fermeture sur les grands ponts !"
                    )
                    prefs.edit().putBoolean(alertKey, true).apply()
                }
            } else {
                if (alreadyNotified) {
                    Log.i("BridgeAlertWorker", "✅ Alerte vent levée en Seine-Maritime.")
                    prefs.edit().putBoolean(alertKey, false).apply()
                }
            }
        } catch (e: Exception) {
            Log.e("BridgeAlertWorker", "❌ Erreur lors de la vérification Météo-France", e)
        }
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

            val bypassGpsCheck = prefs.getBoolean("bypass_gps_check", false)

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

            if (bestLocation != null) {
                val ageMinutes = (System.currentTimeMillis() - bestLocation!!.time) / (1000 * 60)
                val maxAgeMillis = 15 * 60 * 1000L
                if (System.currentTimeMillis() - bestLocation!!.time > maxAgeMillis) {
                    if (bypassGpsCheck) {
                        Log.w("BridgeAlertWorker", "⚠️ Position trop ancienne ($ageMinutes min), mais le mode 'Ignorer l'âge GPS' est activé.")
                    } else {
                        bestLocation = null
                    }
                }
            }

            if (bestLocation == null && !bypassGpsCheck) {
                return
            }

            for (bridge in bridges) {
                val bridgeName = bridge.name
                val coords = BridgeCoordinates.map[bridgeName]
                val isClosed = bridge.status == BridgeStatus.CLOSED

                val isFavorite = favoriteIds.contains(bridge.id) ||
                        favoriteIds.contains(bridgeName) ||
                        legacyFavorites.contains(bridgeName) ||
                        legacyFavorites.contains(bridge.id)

                val wasClosedKey = "was_closed_$bridgeName"
                val wasPreviouslyClosed = prefs.getBoolean(wasClosedKey, false)
                val notifiedReopenedKey = "notified_reopened_$bridgeName"
                val alreadyNotifiedReopened = prefs.getBoolean(notifiedReopenedKey, false)

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

                if (wasPreviouslyClosed && !isClosed) {
                    if (!alreadyNotifiedReopened) {
                        val distanceConditionMet = bypassGpsCheck || (distanceInKm != null && distanceInKm <= 3.0)
                        if (isFavorite && distanceConditionMet) {
                            helper.sendNowOpenNotification(
                                bridgeName,
                                "Bonne nouvelle : $bridgeName est à nouveau ouvert à la circulation !"
                            )
                            prefs.edit().putBoolean(notifiedReopenedKey, true).apply()
                        }
                    }
                    prefs.edit().putBoolean(wasClosedKey, false).apply()
                }

                val distanceConditionMetClosure = bypassGpsCheck || (distanceInKm != null && distanceInKm <= 10.0)
                if (isFavorite && isClosed && distanceConditionMetClosure) {
                    val notifKey = "notified_closed_$bridgeName"
                    if (!prefs.getBoolean(notifKey, false)) {
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
        }

        fun schedulePeriodicCheck(context: Context) {
            scheduleNextCheck(context, policy = ExistingWorkPolicy.KEEP)
        }
    }
}