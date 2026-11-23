package com.example.transfer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.R

class TransferForegroundService : Service() {

  companion object {
    const val CHANNEL_ID = "ammar_share_transfer_channel"
    const val NOTIFICATION_ID = 4825
    const val EXTRA_TITLE = "extra_title"
    const val EXTRA_TEXT = "extra_text"

    fun start(context: Context, title: String, text: String) {
      val intent =
        Intent(context, TransferForegroundService::class.java).apply {
          putExtra(EXTRA_TITLE, title)
          putExtra(EXTRA_TEXT, text)
        }
      try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          context.startForegroundService(intent)
        } else {
          context.startService(intent)
        }
      } catch (_: Exception) {}
    }

    fun stop(context: Context) {
      try {
        context.stopService(Intent(context, TransferForegroundService::class.java))
      } catch (_: Exception) {}
    }
  }

  override fun onCreate() {
    super.onCreate()
    try {
      createNotificationChannel()
    } catch (_: Throwable) {}
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    try {
      val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Fast Share"
      val text = intent?.getStringExtra(EXTRA_TEXT) ?: "Turbo transfer engine active"
      val notification = buildNotification(title, text)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        startForeground(
          NOTIFICATION_ID,
          notification,
          ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
      } else {
        startForeground(NOTIFICATION_ID, notification)
      }
    } catch (_: Throwable) {
      stopSelf()
    }
    return START_NOT_STICKY
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
      val channel =
        NotificationChannel(
            CHANNEL_ID,
            "Fast Share Transfers",
            NotificationManager.IMPORTANCE_LOW,
          )
          .apply { description = "Shows active high-speed file transfer status" }
      nm.createNotificationChannel(channel)
    }
  }

  private fun buildNotification(title: String, text: String): Notification {
    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle(title)
      .setContentText(text)
      .setOngoing(true)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .build()
  }
}
