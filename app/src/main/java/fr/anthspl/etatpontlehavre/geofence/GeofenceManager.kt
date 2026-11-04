package fr.anthspl.etatpontlehavre.geofence

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import fr.anthspl.etatpontlehavre.data.model.BridgeCoordinates
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Gestionnaire du Geofencing et du calcul dynamique de fréquence selon la distance des ponts.
 */
object GeofenceManager {

    private const val CENTER_LAT = 49.486004548921755
    private const val CENTER_LON = 0.1555289973040907

    private const val GEOFENCE_REQ_CODE = 1001

    /**
     * Récupère la position active fraîche via FusedLocationProviderClient (Google Play Services).
     */
    @SuppressLint("MissingPermission")
    suspend fun fetchFreshLocation(context: Context): Location? {
        return suspendCancellableCoroutine { continuation ->
            try {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { lastLoc ->
                        if (lastLoc != null) {
                            if (continuation.isActive) continuation.resume(lastLoc, null)
                        } else {
                            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                                .addOnSuccessListener { currentLoc ->
                                    if (continuation.isActive) continuation.resume(currentLoc, null)
                                }
                                .addOnFailureListener {
                                    if (continuation.isActive) continuation.resume(null, null)
                                }
                        }
                    }
                    .addOnFailureListener {
                        if (continuation.isActive) continuation.resume(null, null)
                    }
            } catch (e: Exception) {
                Log.e("GeofenceManager", "❌ Erreur FusedLocation : ${e.message}")
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }

    /**
     * Calcule la distance minimale entre la position courante de l'utilisateur et le pont le plus proche (en km).
     */
    fun calculateMinDistanceToBridgesInKm(userLocation: Location?): Double? {
        if (userLocation == null) return null

        var minDistanceKm: Double? = null
        for (coords in BridgeCoordinates.map.values) {
            val results = FloatArray(1)
            Location.distanceBetween(
                userLocation.latitude, userLocation.longitude,
                coords.first, coords.second,
                results
            )
            val distanceKm = results[0] / 1000.0
            if (minDistanceKm == null || distanceKm < minDistanceKm) {
                minDistanceKm = distanceKm
            }
        }
        Log.d("GeofenceManager", "Distance actuelle calculée : $minDistanceKm km")
        return minDistanceKm
    }

    /**
     * Calcule la fréquence automatique recommandée (en minutes) :
     * - <= 10 km : 1 min
     * - <= 15 km : 5 min
     * - <= 20 km : 10 min
     * - <= 30 km : 30 min
     * - > 30 km  : 60 min (1 heure)
     */
    fun getIntervalForDistance(distanceKm: Double?): Int {
        if (distanceKm == null) return 5 // Valeur par défaut si position inconnue

        return when {
            distanceKm <= 10.0 -> 1   // 1 minute si < 10km
            distanceKm <= 15.0 -> 5   // 5 minutes si < 15km
            distanceKm <= 20.0 -> 10  // 10 minutes si < 20km
            distanceKm <= 30.0 -> 30  // 30 minutes si < 30km
            else -> 60                // 60 minutes (1h) si > 30km (ou > 50km)
        }
    }

    @SuppressLint("MissingPermission")
    fun setupGeofences(context: Context) {
        try {
            val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)

            val geofenceList = listOf(
                createGeofence("GEOFENCE_10KM", 10_000f),
                createGeofence("GEOFENCE_15KM", 15_000f),
                createGeofence("GEOFENCE_20KM", 20_000f),
                createGeofence("GEOFENCE_30KM", 30_000f),
                createGeofence("GEOFENCE_50KM", 50_000f)
            )

            val request = GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER or GeofencingRequest.INITIAL_TRIGGER_EXIT)
                .addGeofences(geofenceList)
                .build()

            val pendingIntent = getGeofencePendingIntent(context)

            geofencingClient.addGeofences(request, pendingIntent)
                .addOnSuccessListener {
                    Log.i("GeofenceManager", "✅ Geofences (10km, 15km, 20km, 30km, 50km) enregistrées avec succès.")
                }
                .addOnFailureListener { e ->
                    Log.e("GeofenceManager", "❌ Échec enregistrement Geofences : ${e.message}", e)
                }
        } catch (e: Exception) {
            Log.e("GeofenceManager", "❌ Erreur setupGeofences : ${e.message}", e)
        }
    }

    fun removeGeofences(context: Context) {
        try {
            val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)
            val pendingIntent = getGeofencePendingIntent(context)
            geofencingClient.removeGeofences(pendingIntent)
                .addOnSuccessListener {
                    Log.i("GeofenceManager", "ℹ️ Geofences supprimées (mode fixe activé).")
                }
        } catch (e: Exception) {
            Log.e("GeofenceManager", "❌ Erreur removeGeofences", e)
        }
    }

    private fun createGeofence(id: String, radiusInMeters: Float): Geofence {
        return Geofence.Builder()
            .setRequestId(id)
            .setCircularRegion(CENTER_LAT, CENTER_LON, radiusInMeters)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()
    }

    private fun getGeofencePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            GEOFENCE_REQ_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }
}