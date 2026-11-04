package fr.anthspl.etatpontlehavre

import android.app.Application
import android.content.Context
import fr.anthspl.etatpontlehavre.geofence.GeofenceManager
import fr.anthspl.etatpontlehavre.notifications.BridgeAlertWorker

class EtatPontLeHavre : Application() {
    override fun onCreate() {
        super.onCreate()
        // Planifie la vérification périodique des ponts dès le lancement de l'application
        BridgeAlertWorker.schedulePeriodicCheck(this)

        // Initialise les Geofences GPS si le mode automatique est actif
        val prefs = getSharedPreferences("bridge_alert_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("use_auto_geofence_interval", true)) {
            GeofenceManager.setupGeofences(this)
        }
    }
}
