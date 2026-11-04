package app.sentinela.session.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import app.sentinela.MainActivity
import app.sentinela.R

/**
 * Notificação persistente obrigatória do serviço de primeiro plano. O Sentinela
 * é transparente por design: a gravação é sempre visível ao usuário por esta
 * notificação e pelos indicadores de câmera/microfone do Android. O app nunca
 * tenta ocultar que está gravando.
 */
object RecordingNotifications {
    const val CHANNEL_ID = "sentinela_recording"
    const val NOTIFICATION_ID = 0x5E17

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notif_channel_desc)
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun build(context: Context, contentText: String, showStop: Boolean): Notification {
        ensureChannel(context)
        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notif_title))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_sentinela_notification)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openIntent)

        if (showStop) {
            val stopIntent = PendingIntent.getService(
                context,
                1,
                RecordingService.stopIntent(context),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(0, context.getString(R.string.notif_action_stop), stopIntent)
        }
        return builder.build()
    }
}
