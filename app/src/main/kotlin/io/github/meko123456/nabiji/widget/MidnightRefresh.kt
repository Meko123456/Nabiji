package io.github.meko123456.nabiji.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Redraws the steps widget when the day changes.
 *
 * The widget shows today's steps, and nothing told it the day was over. updatePeriodMillis is
 * best-effort and held back while the phone dozes, which is most of the night, so mornings started
 * with yesterday's count still on the home screen. The same fix as HabitStreaks' widget.
 *
 * The alarm does not wake the phone. If the phone is asleep at midnight the alarm fires when it next
 * wakes, which is the first moment anyone could look at the widget.
 */
object MidnightRefresh {

    /** The shortest window Android allows an app without exact alarms; it widens anything less. */
    private const val WINDOW_MS = 10 * 60 * 1000L

    /** Sets (or moves) the alarm to the next local midnight. */
    fun schedule(context: Context) {
        val now = ZonedDateTime.now()
        val at = now.toLocalDate().plusDays(1).atStartOfDay(now.zone).toInstant().toEpochMilli()
        context.getSystemService(AlarmManager::class.java)
            .setWindow(AlarmManager.RTC, at, WINDOW_MS, pending(context))
    }

    /** Once the last steps widget is gone. */
    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(context))
    }

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, MidnightRefreshReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

/** Fired by [MidnightRefresh]: redraw for the new day, which also sets the next alarm. */
class MidnightRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                StepsWidget.refresh(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}
