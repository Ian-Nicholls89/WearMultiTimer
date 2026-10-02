package io.github.iannicholls89.wearmultitimer.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import io.github.iannicholls89.wearmultitimer.MainActivity
import io.github.iannicholls89.wearmultitimer.R
import io.github.iannicholls89.wearmultitimer.RingActivity
import io.github.iannicholls89.wearmultitimer.timer.TimerItem
import io.github.iannicholls89.wearmultitimer.timer.displayName

object Notifications {
    const val RINGING_ID = 1
    private const val FINISHED_ID = 2
    const val RUNNING_ID = 3
    private const val RINGING_CHANNEL = "ringing"
    private const val FINISHED_CHANNEL = "finished"
    private const val RUNNING_CHANNEL = "running"

    private fun channels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(RINGING_CHANNEL, "Time's up", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "A timer that has just run out"
                // The ringing service buzzes and plays the sound itself, for as long as it rings.
                setSound(null, null)
                enableVibration(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(RUNNING_CHANNEL, "Running timers", NotificationManager.IMPORTANCE_LOW).apply {
                description = "The running timer, at the foot of the watch face"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(FINISHED_CHANNEL, "Missed timers", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Timers that rang out without being stopped"
            },
        )
    }

    /** Covers both the permission (Android 13+) and notifications switched off for the app. */
    fun canPost(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Opens the full-screen alert. */
    fun ringScreenIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 1,
        Intent(context, RingActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(context: Context, action: String, code: Int) = PendingIntent.getBroadcast(
        context, code,
        Intent(context, TimerActionReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun title(done: List<TimerItem>) =
        if (done.size == 1) done[0].displayName() else "${done.size} timers"

    /** The ringing service's notification: shown full screen when the watch is asleep. */
    fun ringing(context: Context, ringing: List<TimerItem>): Notification {
        channels(context)
        val open = ringScreenIntent(context)
        return NotificationCompat.Builder(context, RINGING_CHANNEL)
            .setSmallIcon(R.drawable.ic_timer_small)
            .setContentTitle(if (ringing.isEmpty()) "Time's up" else title(ringing))
            .setContentText(
                if (ringing.size > 1) ringing.joinToString(", ") { it.displayName() } + " - time's up"
                else "Time's up",
            )
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .apply {
                // Counts up the overtime, as the timer's own screen does.
                ringing.singleOrNull()?.endAtMs?.let { setWhen(it).setShowWhen(true).setUsesChronometer(true) }
            }
            .addAction(R.drawable.ic_stop, "Stop", action(context, TimerActionReceiver.ACTION_STOP, 10))
            .addAction(R.drawable.ic_add, "+1 min", action(context, TimerActionReceiver.ACTION_ADD_MINUTE, 11))
            .build()
    }

    /**
     * Timers that rang out without being stopped (or were silenced on the charger): a silent
     * "Missed" reminder, saying when they ended, until they're stopped.
     */
    fun showFinished(context: Context, timers: List<TimerItem>, now: Long) {
        val nm = NotificationManagerCompat.from(context)
        val done = timers.filter { it.isDone(now) }
        if (done.isEmpty() || !canPost(context)) {
            nm.cancel(FINISHED_ID)
            return
        }
        channels(context)
        val open = PendingIntent.getActivity(
            context, 2, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val ended = DateFormat.getTimeFormat(context).format(java.util.Date(done.minOf { it.endAtMs!! }))
        val n = NotificationCompat.Builder(context, FINISHED_CHANNEL)
            .setSmallIcon(R.drawable.ic_timer_small)
            .setContentTitle(title(done))
            .setContentText(if (done.size == 1) "Missed · ended at $ended" else "Missed · first ended at $ended")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(R.drawable.ic_stop, "Stop", action(context, TimerActionReceiver.ACTION_STOP, 10))
            .build()
        try {
            nm.notify(FINISHED_ID, n)
        } catch (_: SecurityException) {
            // Notifications switched off between the check and here.
        }
    }

    /**
     * While any timer runs, its icon sits at the foot of the watch face (an Ongoing Activity)
     * counting down the soonest one; tapping it opens the app. Gone when none is running.
     */
    fun showRunning(context: Context, timers: List<TimerItem>, now: Long) {
        val nm = NotificationManagerCompat.from(context)
        val running = timers
            .filter { it.state == TimerItem.State.RUNNING && !it.isDone(now) }
            .sortedBy { it.endAtMs }
        if (running.isEmpty() || !canPost(context)) {
            nm.cancel(RUNNING_ID)
            return
        }
        channels(context)
        val next = running.first()
        val title = next.displayName() + if (running.size > 1) " +${running.size - 1}" else ""
        val open = PendingIntent.getActivity(
            context, 3, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, RUNNING_CHANNEL)
            .setSmallIcon(R.drawable.ic_timer_small)
            .setContentTitle(title)
            .setContentText(if (running.size > 1) "${running.size} timers running" else "Running")
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setWhen(next.endAtMs!!)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
        // The watch counts the part down itself, from the time since it started up.
        val endOnBootClock = SystemClock.elapsedRealtime() + (next.endAtMs - now)
        OngoingActivity.Builder(context, RUNNING_ID, builder)
            .setStaticIcon(R.drawable.ic_timer_small)
            .setTouchIntent(open)
            .setStatus(
                Status.Builder()
                    .addTemplate("#name# #time#")
                    .addPart("name", Status.TextPart(title))
                    .addPart("time", Status.TimerPart(endOnBootClock))
                    .build(),
            )
            .build()
            .apply(context)
        try {
            nm.notify(RUNNING_ID, builder.build())
        } catch (_: SecurityException) {
        }
    }
}
