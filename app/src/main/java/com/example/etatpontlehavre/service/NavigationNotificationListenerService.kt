package com.example.etatpontlehavre.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.etatpontlehavre.data.model.BridgeCoordinates
import com.example.etatpontlehavre.notifications.BridgeNotificationHelper

/**
 * Service d'écoute intelligent : dès qu'une navigation GPS est active (Waze, Roole, Maps),
 * il surveille si vous vous approchez d'un pont du Havre (dans un rayon de 3 km)
 * pour vous prévenir bien à l'avance et vous laisser le temps de changer de route.
 */
class NavigationNotificationListenerService : NotificationListenerService() {

    private val navPackages = setOf(
        "com.waze",
        "com.google.android.apps.maps",
        "com.roole.app",
        "com.sygic.aura"
    )

    // Anti-spam pour éviter de renvoyer la même notification en boucle toutes les 10 secondes
    private val alertedBridges = mutableSetOf<String>()
    private var lastCheckTime = 0L

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val packageName = sbn.packageName ?: return

        // Si une application de navigation est active et envoie des mises à jour de trajet
        if (navPackages.contains(packageName)) {
            val currentTime = System.currentTimeMillis()

            // On vérifie toutes les 30 secondes maximum lors du trajet pour préserver la batterie
            if (currentTime - lastCheckTime > 30_000) {
                lastCheckTime = currentTime
                checkNearbyBridges()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun checkNearbyBridges() {
        try {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = locationManager.getProviders(true)
            var bestLocation: Location? = null

            for (provider in providers) {
                val loc = locationManager.getLastKnownLocation(provider) ?: continue
                if (bestLocation == null || loc.time > bestLocation.time) {
                    bestLocation = loc
                }
            }

            bestLocation ?: return

            // On parcourt tous les ponts du Havre
            for ((bridgeName, coords) in BridgeCoordinates.map) {
                val results = FloatArray(1)
                Location.distanceBetween(
                    bestLocation.latitude, bestLocation.longitude,
                    coords.first, coords.second,
                    results
                )

                val distanceInKm = results[0] / 1000.0

                // Si le pont est à moins de 3 km de votre position actuelle en pleine navigation
                if (distanceInKm <= 3.0) {
                    // Si on ne l'a pas déjà alerte récemment pour ce trajet
                    if (!alertedBridges.contains(bridgeName)) {
                        alertedBridges.add(bridgeName)

                        val helper = BridgeNotificationHelper(applicationContext)
                        helper.sendNavigationRouteWarningNotification(
                            bridgeName,
                            "Attention : $bridgeName est à moins de 3 km de votre position !"
                        )
                    }
                } else {
                    // Si l'utilisateur s'éloigne, on réinitialise l'alerte pour ce pont
                    alertedBridges.remove(bridgeName)
                }
            }
        } catch (_: Exception) {
            // Gestion silencieuse des erreurs GPS
        }
    }
}