package com.hihi.ttsserver.service

import android.app.Notification
import android.app.NotificationManager
import android.app.NotificationChannel
import android.content.Context
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.hihi.ttsserver.R
import com.hihi.ttsserver.ui.MainActivity

object NotificationHelper {
    private var notificationManager: NotificationManager? = null
    private var notificationId: Int = 1
    private var context: Context? = null
    private const val NOTIFICATION_CHAN_ID = "zz_tts_service"

    fun init(context: Context, notificationId: Int = 1) {
        this.context = context
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        this.notificationId = notificationId
        // 只支持8.0+，直接创建NotificationChannel
        val channel = NotificationChannel(
            NOTIFICATION_CHAN_ID,
            "TTS服务",
            NotificationManager.IMPORTANCE_LOW
        )
        channel.description = "TTS前台服务通知"
        notificationManager?.createNotificationChannel(channel)
    }

    fun updateNotification(notification: Notification) {
        notificationManager?.notify(notificationId, notification)
    }

    fun updateNotification(title: String, content: String? = null) {
        context?.let { ctx ->
            val notification = createNotification(ctx, title, content ?: "")
            updateNotification(notification)
        }
    }

    fun createNotification(context: Context, title: String, content: String): Notification {
        val stopIntent = Intent("com.hihi.ttsserver.action.STOP").apply {
            `package` = context.packageName
        }

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, NOTIFICATION_CHAN_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_tts_24px)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .build()
    }
} 