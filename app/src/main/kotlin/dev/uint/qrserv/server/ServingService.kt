package dev.uint.qrserv.server

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.uint.qrserv.MainActivity
import dev.uint.qrserv.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ServingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observing = false
    private var lastStartId = 0
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        // Not started via startForegroundService(), so no startForeground() here -- it could be refused from the background.
        if (intent?.action == ACTION_STOP) {
            if (!ServingState.requestStop()) stopCleanly()
            return START_NOT_STICKY
        }

        // Must come first: a startForegroundService() that doesn't reach startForeground() in time crashes.
        promote(ServingState.notice.value)
        if (!observing) {
            observing = true
            observe()
        }
        return START_NOT_STICKY
    }

    private fun observe() {
        scope.launch {
            ServingState.notice.collect { notice ->
                if (notice == null) stopCleanly() else post(notice)
            }
        }
        scope.launch {
            ServingState.activeTransfers.map { it > 0 }.distinctUntilChanged().collect(::holdWakeLock)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        ServingState.notice.value?.let(::post)
    }

    private fun post(notice: ServingNotice) {
        if (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(notice))
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!ServingState.requestStop()) stopCleanly()
    }

    override fun onDestroy() {
        scope.cancel()
        holdWakeLock(false)
        super.onDestroy()
    }

    private fun promote(notice: ServingNotice?) {
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(notice), type)
    }

    private fun stopCleanly() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        // stopSelf(startId): a start queued after this one keeps the service alive.
        stopSelf(lastStartId)
    }

    private fun holdWakeLock(held: Boolean) {
        if (held) {
            val lock = wakeLock ?: (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "qrserv:transfer")
                .also {
                    it.setReferenceCounted(false)
                    wakeLock = it
                }
            lock.acquire(WAKE_LOCK_TIMEOUT_MS)
        } else {
            wakeLock?.takeIf { it.isHeld }?.release()
        }
    }

    private fun buildNotification(notice: ServingNotice?): Notification {
        ensureChannel()
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val publicVersion = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_public_title))
            .build()

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        when (notice) {
            is ServingNotice.Sharing -> {
                val stop = PendingIntent.getService(
                    this,
                    1,
                    Intent(this, ServingService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_IMMUTABLE,
                )
                builder
                    .setContentTitle(getString(R.string.notification_sharing_title, notice.fileName))
                    .setContentText(
                        notice.hotspotSsid?.let { getString(R.string.notification_hotspot_detail, it) }
                            ?: notice.address,
                    )
                    .addAction(0, getString(R.string.notification_action_stop), stop)
            }

            ServingNotice.StartingHotspot -> {
                builder.setContentTitle(getString(R.string.notification_starting_hotspot))
            }

            else -> {
                builder.setContentTitle(getString(R.string.notification_preparing_title))
            }
        }
        return builder.build()
    }

    private fun ensureChannel() {
        NotificationManagerCompat.from(this).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(getString(R.string.notification_channel_name))
                .setShowBadge(false)
                .build(),
        )
    }

    companion object {
        private const val CHANNEL_ID = "sharing"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "dev.uint.qrserv.action.STOP_SHARING"

        // Safety net against a lost release.
        private const val WAKE_LOCK_TIMEOUT_MS = 60 * 60 * 1000L

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, ServingService::class.java))
            } catch (_: IllegalStateException) {
                // Refused on Android 12+ once the app has left the foreground; sharing just won't survive backgrounding.
            }
        }
    }
}
