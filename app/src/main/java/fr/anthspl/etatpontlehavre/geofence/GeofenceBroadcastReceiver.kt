package fr.anthspl.etatpontlehavre.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import fr.anthspl.etatpontlehavre.notifications.BridgeAlertWorker

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return

        if (geofencingEvent.hasError()) {
            Log.e("GeofenceReceiver", "❌ Erreur Geofencing : ${geofencingEvent.errorCode}")
            return
        }

        val transition = geofencingEvent.geofenceTransition
        val triggeringGeofences = geofencingEvent.triggeringGeofences
        val triggeringLocation = geofencingEvent.triggeringLocation

        // Récupérer l'identifiant de la geofence qui a déclenché l'événement (ex: "GEOFENCE_10KM")
        val geofenceId = triggeringGeofences?.firstOrNull()?.requestId ?: "INCONNU"

        val transitionType = when (transition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> "ENTRÉE"
            Geofence.GEOFENCE_TRANSITION_EXIT -> "SORTIE"
            else -> "TRANSITION_$transition"
        }

        Log.i("GeofenceReceiver", "🚨 [Geofence] $transitionType de la zone [$geofenceId]")

        // Optionnel : tu peux ajuster directement une logique ciblée selon l'ID franchi,
        // ou laisser ton worker recalculer la distance exacte via le GPS.
        when (geofenceId) {
            "GEOFENCE_10KM" -> {
                if (transition == Geofence.GEOFENCE_TRANSITION_ENTER) {
                    Log.i("GeofenceReceiver", "⚡ Proximité immédiate (< 10km) détectée par Geofence ! Passage en mode intensif.")
                }
            }
            "GEOFENCE_50KM" -> {
                if (transition == Geofence.GEOFENCE_TRANSITION_EXIT) {
                    Log.i("GeofenceReceiver", "🌍 L'utilisateur s'éloigne à plus de 50km.")
                }
            }
        }

        val prefs = context.getSharedPreferences("bridge_alert_prefs", Context.MODE_PRIVATE)
        val isAutoGeofenceActive = prefs.getBoolean("use_auto_geofence_interval", true)

        if (isAutoGeofenceActive) {
            // Relance le worker avec la position de déclenchement pour recalculer proprement l'intervalle
            BridgeAlertWorker.scheduleNextCheck(context, userLocation = triggeringLocation)
        }
    }
}