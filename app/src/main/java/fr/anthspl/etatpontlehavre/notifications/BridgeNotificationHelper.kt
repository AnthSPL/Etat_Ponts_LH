package fr.anthspl.etatpontlehavre.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import fr.anthspl.etatpontlehavre.MainActivity
import fr.anthspl.etatpontlehavre.R
import fr.anthspl.etatpontlehavre.data.model.Bridge

/**
 * Assistant de création et d'émission des notifications push système pleinement optimisées pour Android Auto et smartphone.
 */
class BridgeNotificationHelper(private val context: Context) {

    private val channelId = "bridge_alert_channel"

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Alertes Ponts Le Havre"
            val descriptionText = "Notifications d'alerte et de réouverture des ponts compatibles Android Auto"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createAppPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createCarAction(pendingIntent: PendingIntent): NotificationCompat.Action {
        return NotificationCompat.Action.Builder(
            R.drawable.ic_bridge,
            "Voir dans l'application",
            pendingIntent
        ).build()
    }

    fun sendClosureAlertNotification(bridge: Bridge, message: String = "Fermeture détectée sur ce pont") {
        sendClosureAlertNotification(bridge.name, message)
    }

    /**
     * Notification pour la fermeture d'un pont favori.
     */
    fun sendClosureAlertNotification(bridgeName: String, message: String = "Attention : Ce pont est fermé !") {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val pendingIntent = createAppPendingIntent()
        val carAction = createCarAction(pendingIntent)

        val fullTitle = "⚠️ Pont Fermé : $bridgeName"

        val carExtender = NotificationCompat.CarExtender()
            .setColor(0xFFD32F2F.toInt())

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_warning)
            .setContentTitle(fullTitle)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .addAction(carAction)
            .setAutoCancel(true)
            .extend(carExtender)
            .build()

        notificationManager.notify(bridgeName.hashCode(), notification)
    }

    /**
     * Notification de rappel pour un pont restant fermé depuis plus de 2 heures.
     */
    fun sendClosureReminderNotification(
        bridgeName: String,
        message: String = "Rappel : $bridgeName est toujours fermé à la circulation."
    ) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val pendingIntent = createAppPendingIntent()
        val carAction = createCarAction(pendingIntent)

        val fullTitle = "⌛ Rappel Fermeture : $bridgeName"

        val carExtender = NotificationCompat.CarExtender()
            .setColor(0xFFE65100.toInt())

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_warning)
            .setContentTitle(fullTitle)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .addAction(carAction)
            .setAutoCancel(true)
            .extend(carExtender)
            .build()

        // Même identifiant que la fermeture initiale pour mettre à jour la notification sur le téléphone
        notificationManager.notify(bridgeName.hashCode(), notification)
    }

    /**
     * Notification pour la réouverture d'un pont favori.
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

        val pendingIntent = createAppPendingIntent()
        val carAction = createCarAction(pendingIntent)

        val fullTitle = "🔵 Réouverture Proche : $bridgeName"
        val message = "$bridgeName est BIENTÔT OUVERT aux véhicules ! La manœuvre se termine."

        val carExtender = NotificationCompat.CarExtender()
            .setColor(0xFF0288D1.toInt())

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(fullTitle)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .addAction(carAction)
            .setAutoCancel(true)
            .extend(carExtender)
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

        val pendingIntent = createAppPendingIntent()
        val carAction = createCarAction(pendingIntent)

        val fullTitle = "✅ Pont Ouvert : $bridgeName"

        val carExtender = NotificationCompat.CarExtender()
            .setColor(0xFF2E7D32.toInt())

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_tick)
            .setContentTitle(fullTitle)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .addAction(carAction)
            .setAutoCancel(true)
            .extend(carExtender)
            .build()

        // Identifiant unique unifié pour la réouverture (hashCode + 200) afin d'éviter tout doublon
        notificationManager.notify(bridgeName.hashCode() + 200, notification)
    }
}