package io.github.iannicholls89.wearmultitimer.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.iannicholls89.wearmultitimer.MainActivity
import io.github.iannicholls89.wearmultitimer.timer.TimerItem
import io.github.iannicholls89.wearmultitimer.timer.nextAlarmAt

/**
 * One alarm, for whichever running timer finishes next. When it goes off, [TimerAlarmReceiver]
 * starts the ringing and sets the alarm for the one after.
 */
object AlarmScheduler {

    fun set(context: Context, timers: List<TimerItem>, now: Long = System.currentTimeMillis()) {
        val am = context.getSystemService(AlarmManager::class.java)
        val operation = alarmIntent(context)
        val at = timers.nextAlarmAt(now)
        if (at == null) {
            am.cancel(operation)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            // Not expected (USE_EXACT_ALARM is granted on install), but late beats never.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        } else {
            // An alarm clock: exact, and it wakes the watch from its deepest sleep, as a timer must.
            val show = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), operation)
        }
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, TimerAlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
