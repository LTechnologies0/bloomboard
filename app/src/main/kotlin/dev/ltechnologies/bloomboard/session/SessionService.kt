package dev.ltechnologies.bloomboard.session

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.ltechnologies.bloomboard.BloomboardApp
import dev.ltechnologies.bloomboard.MainActivity
import dev.ltechnologies.bloomboard.R

class SessionService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as BloomboardApp
        startForegroundWithType()
        if (app.preferences.settings.value.hidEnabled) app.hid.start() else app.hid.stop()
        if (app.preferences.settings.value.tvActive) app.tv.restore()
        if (!app.preferences.settings.value.hidEnabled && !app.preferences.settings.value.tvActive) {
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        (application as BloomboardApp).hid.stop()
        super.onDestroy()
    }

    private fun startForegroundWithType() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL, getString(R.string.service_channel), NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(channel)
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(getString(R.string.service_title))
            .setContentText(getString(R.string.service_text))
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL = "bloomboard-session"
        private const val NOTIFICATION_ID = 7

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, SessionService::class.java))
        }
    }
}
