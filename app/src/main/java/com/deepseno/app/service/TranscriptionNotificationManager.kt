package com.enmooy.deepseno.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.enmooy.deepseno.MainActivity
import com.enmooy.deepseno.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranscriptionNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val CHANNEL_ID = "deepseno_transcription"
        private const val NOTIFICATION_BASE_ID = 2000
    }

    private var notificationId = NOTIFICATION_BASE_ID

    init {
        createChannel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Transcription",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Transcription completion notifications"
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    fun sendTranscriptionComplete(recordingName: String) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Transcription Complete")
            .setContentText("\"$recordingName\" has been transcribed")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(notificationId++, notification)
    }
}
