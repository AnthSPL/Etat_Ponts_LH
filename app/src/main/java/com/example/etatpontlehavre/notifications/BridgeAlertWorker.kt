package com.example.etatpontlehavre.notifications

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.etatpontlehavre.data.model.BridgeCoordinates
import com.example.etatpontlehavre.data.model.BridgeStatus
import com.example.etatpontlehavre.data.remote.SWingDataSource
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

class BridgeAlertWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    private val prefs = context.getSharedPreferences("bridge_alert_prefs", Context.MODE_PRIVATE)

    override fun doWork(): Result {
        runBlocking {
            checkBridgesAndNotify()
        }
        return Result.success()
    }

    @SuppressLint("MissingPermission")
    private suspend fun checkBridgesAndNotify() {
        try {
            val dataSource = SWingDataSource()
            val bridges = dataSource.fetchLeHavreBridges()

            val favorites = prefs.getStringSet("favorite_bridges", emptySet()) ?: emptySet()
            val helper = BridgeNotificationHelper(applicationContext)

            val locationManager = applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            val gpsLoc = if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            } else null

            val netLoc = if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            } else null

            var bestLocation: Location? = null
            listOfNotNull(gpsLoc, netLoc).forEach { loc ->
                if (bestLocation == null || loc.time > bestLocation.time) {
                    bestLocation = loc
                }
            }

            for (bridge in bridges) {
                val bridgeName = bridge.name
                val coords = BridgeCoordinates.map[bridgeName]
                val isClosed = bridge.status == BridgeStatus.CLOSED
                val isFavorite = favorites.contains(bridgeName)

                val wasClosedKey = "was_closed_$bridgeName"
                val wasPreviouslyClosed = prefs.getBoolean(wasClosedKey, false)

                // Calcul de la distance entre l'utilisateur et le pont
                var distanceInKm: Double? = null
                if (coords != null && bestLocation != null) {
                    val results = FloatArray(1)
                    Location.distanceBetween(
                        bestLocation.latitude, bestLocation.longitude,
                        coords.first, coords.second,
                        results
                    )
                    distanceInKm = results[0] / 1000.0
                }

                // --- 1. Notification de Réouverture (Fermé -> Ouvert) ---
                // Uniquement si pont favori ET distance <= 3 km
                if (wasPreviouslyClosed && !isClosed) {
                    if (isFavorite && distanceInKm != null && distanceInKm <= 300.0) {
                        helper.sendReopeningNotification(
                            bridgeName,
                            "Bonne nouvelle : $bridgeName est à nouveau ouvert à la circulation !"
                        )
                    }
                }

                // --- 2. Notification de Fermeture (Favori + Fermé + distance <= 10 km) ---
                if (isFavorite && isClosed && distanceInKm != null && distanceInKm <= 1000.0) {
                    val notifKey = "notified_closed_$bridgeName"
                    if (!prefs.getBoolean(notifKey, false)) {
                        helper.sendClosureAlertNotification(
                            bridgeName,
                            "Attention : $bridgeName est fermé à moins de 10 km de vous !"
                        )
                        prefs.edit().putBoolean(notifKey, true).apply()
                    }
                } else {
                    prefs.edit().remove("notified_closed_$bridgeName").apply()
                }

                // --- 3. Mise à jour de l'état mémorisé du pont ---
                prefs.edit().putBoolean(wasClosedKey, isClosed).apply()
            }

        } catch (_: Exception) {
        }
    }

    companion object {
        fun schedulePeriodicCheck(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<BridgeAlertWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "BridgeAlertWork",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}
