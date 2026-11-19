package com.mohdrozaimi.statuspro4k.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.mohdrozaimi.statuspro4k.MainActivity

/** Keeps the export alive in the background (screen off / app switched) with a progress notification. */
class ProcessingService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel(this)
        val n = build(this, 0, "Preparing…")
        val type = if (Build.VERSION.SDK_INT >= 35) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
        else ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        startForeground(NOTIF_ID, n, type)
        if (wakeLock == null) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "StatusPro4K:export").apply {
                setReferenceCounted(false); acquire(3 * 60 * 60 * 1000L)
            }
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "processing"
        private const val NOTIF_ID = 4060
        @Volatile private var lastPercent = -1

        fun intent(ctx: Context) = Intent(ctx, ProcessingService::class.java)

        fun stop(ctx: Context) {
            lastPercent = -1
            ctx.stopService(intent(ctx))
            val nm = ctx.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTIF_ID)
        }

        fun update(ctx: Context, percent: Int, stage: String) {
            if (percent == lastPercent) return
            lastPercent = percent
            ensureChannel(ctx)
            val nm = ctx.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            runCatching { nm.notify(NOTIF_ID, build(ctx, percent, stage)) }
        }

        private fun ensureChannel(ctx: Context) {
            val nm = ctx.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "Video processing", NotificationManager.IMPORTANCE_LOW))
            }
        }

        private fun build(ctx: Context, percent: Int, stage: String): Notification {
            val pi = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            return NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle("StatusPro 4K · $percent%")
                .setContentText(stage)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setProgress(100, percent, false)
                .setContentIntent(pi)
                .build()
        }
    }
}
