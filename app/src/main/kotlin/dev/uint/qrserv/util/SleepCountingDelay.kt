package dev.uint.qrserv.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val WAKE_FOR_WORK = 5.seconds

private val SHORTEST_INEXACT_DELAY = 10.seconds

/** An inexact alarm may fire up to 75% of its delay late, and tends to in Doze. */
internal fun inexactAlarmDelay(remaining: Duration): Duration =
    if (remaining < SHORTEST_INEXACT_DELAY) remaining else remaining / 1.75

/**
 * A delay that keeps counting in deep sleep, unlike delay(). Only an allow-while-idle alarm gets through
 * Doze. Each is set so its latest firing lands on the deadline, and one firing earlier sets the next.
 */
class SleepCountingDelay(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val action = "${context.packageName}.action.DELAY_ELAPSED"
    private val alarm = PendingIntent.getBroadcast(
        context,
        0,
        Intent(action).setPackage(context.packageName),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    private var elapsed: CompletableDeferred<Unit>? = null
    private var deadline = 0L

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Already sent when the delay it was for ended.
            val waiting = elapsed ?: return
            if (SystemClock.elapsedRealtime() < deadline) {
                arm()
                return
            }
            // Keeps the alarm's own wake going for the work that follows.
            val wake = goAsync()
            handler.postDelayed(wake::finish, WAKE_FOR_WORK.inWholeMilliseconds)
            waiting.complete(Unit)
        }
    }

    init {
        ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun arm() {
        val now = SystemClock.elapsedRealtime()
        val remaining = (deadline - now).coerceAtLeast(0L).milliseconds
        alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, now + inexactAlarmDelay(remaining).inWholeMilliseconds, alarm)
    }

    suspend fun delay(duration: Duration) {
        val current = CompletableDeferred<Unit>()
        elapsed = current
        deadline = SystemClock.elapsedRealtime() + duration.inWholeMilliseconds
        try {
            arm()
            withTimeoutOrNull(duration) { current.await() }
        } finally {
            if (elapsed === current) elapsed = null
            alarms.cancel(alarm)
        }
    }

    fun close() {
        context.unregisterReceiver(receiver)
    }
}
