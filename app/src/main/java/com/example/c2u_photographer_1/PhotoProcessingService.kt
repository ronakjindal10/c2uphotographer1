package com.example.c2u_photographer_1
import PhotoProcessor
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log

class PhotoProcessingService : Service() {

    private lateinit var photoProcessor: PhotoProcessor

    override fun onBind(intent: Intent?): IBinder? {
        return null  // We don't provide binding, so return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, createNotification())

        // Initialize and start photo processing
        photoProcessor = PhotoProcessor() // Initialize your PhotoProcessor here
        photoProcessor.startProcessing()

        return START_STICKY  // Ensures service is restarted if killed
    }

    override fun onDestroy() {
        super.onDestroy()
        photoProcessor.stopProcessing()
        // Clean up any other resources you need to release
    }

    private fun createNotification(): Notification {
        // Create the Foreground Service Notification
        val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel("my_service", "My Background Service")
        } else {
            // If earlier version channel ID is not used
            ""
        }

        return Notification.Builder(this, channelId)
            .setContentTitle("Photo Processing")
            .setContentText("Processing photos in background")
            // This is just an example; you'll want to use something from your drawable resources
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
    }

    private fun createNotificationChannel(channelId: String, channelName: String): String{
        // Create a notification channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(channelId,
                channelName, NotificationManager.IMPORTANCE_NONE)
            chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            val service = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            service.createNotificationChannel(chan)
        }
        return channelId
    }
}
