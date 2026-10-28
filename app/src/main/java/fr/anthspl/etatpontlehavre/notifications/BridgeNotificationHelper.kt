package com.example.etatpontlehavre.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.etatpontlehavre.R
import com.example.etatpontlehavre.data.model.Bridge

/**
 * Assistant de création et d'émission des notifications push système.
 */
class BridgeNotificationHelper(private val context: Context) {

    private val channelId = "bridge_alert_channel"

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Alertes Ponts Le Havre"
            val descriptionText = "Notifications d'alerte et de réouverture des ponts"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun sendClosureAlertNotification(bridge: Bridge, message: String = "Fermeture détectée sur ce pont") {
        sendClosureAlertNotification(bridge.name, message)
    }

    /**
     * Notification pour la fermeture d'un pont favori (icône panneau attention).
     */
    fun sendClosureAlertNotification(bridgeName: String, message: String = "Fermeture détectée sur ce pont") {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val roolePackage = "com.roole.roolemap"
        val intent = context.packageManager.getLaunchIntentForPackage(roolePackage)?.apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        } ?: Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=Le+Havre&mode=d")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_warning)
            .setContentTitle("⚠️ Pont Fermé : $bridgeName")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(bridgeName.hashCode(), notification)
    }

    /**
     * Notification pour la réouverture d'un pont favori (icône coche verte tick).
     */
    fun sendReopeningNotification(
        bridgeName: String,
        message: String = "Bonne nouvelle : $bridgeName est à nouveau ouvert à la circulation !"
    ) {
        sendNowOpenNotification(bridgeName, message)
    }

    /**
     * Notification pour le passage du statut Fermé → Bientôt ouvert aux véhicules (Bleu).
     */
    fun sendSoonOpenNotification(bridgeName: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🔵 Réouverture Proche : $bridgeName")
            .setContentText("$bridgeName est BIENTÔT OUVERT aux véhicules !")
            .setStyle(NotificationCompat.BigTextStyle().bigText("La manœuvre sur $bridgeName se termine. La circulation routière va pouvoir reprendre très prochainement."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(bridgeName.hashCode() + 100, notification)
    }

    /**
     * Notification pour le passage du statut Bientôt ouvert → Ouvert aux véhicules (Vert).
     */
    fun sendNowOpenNotification(
        bridgeName: String,
        message: String = "$bridgeName est désormais OUVERT aux véhicules !"
    ) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val roolePackage = "com.roole.roolemap"
        val intent = context.packageManager.getLaunchIntentForPackage(roolePackage)?.apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        } ?: Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=Le+Havre&mode=d")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_tick)
            .setContentTitle("✅ Pont Ouvert : $bridgeName")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        // Identifiant unique unifié pour la réouverture (hashCode + 200) afin d'éviter tout doublon
        notificationManager.notify(bridgeName.hashCode() + 200, notification)
    }
}
