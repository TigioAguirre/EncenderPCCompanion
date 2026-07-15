package com.example.bandcolorreact.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.bandcolorreact.R
import com.example.bandcolorreact.data.DeviceRepository
import com.example.bandcolorreact.ui.DevicesActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Canal separado del que ya usa MusicButtonListenerService para el aviso
 * local de "botón presionado", así el usuario puede desactivar uno sin
 * afectar el otro desde los ajustes de Android.
 */
class PcStatusMessagingService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_ID = "pc_status_channel"
        private const val NOTIFICATION_ID = 2001
    }

    private val repository = DeviceRepository()

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        CoroutineScope(Dispatchers.IO).launch {
            repository.registerFcmToken(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.notification?.title
            ?: message.data["title"]
            ?: getString(R.string.pc_status_notification_title)
        val body = message.notification?.body
            ?: message.data["body"]
            ?: getString(R.string.pc_status_notification_body)

        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        ensureChannel()

        val openAppIntent = Intent(this, DevicesActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_encenderpc)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.pc_status_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.pc_status_channel_desc)
        }
        manager.createNotificationChannel(channel)
    }
}
