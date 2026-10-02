package io.github.iannicholls89.wearmultitimer.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.iannicholls89.wearmultitimer.timer.TimerItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Keeps the receiver alive while the saved timers are read and changed. */
private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.Default).launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

/** The alarm: a timer has run out. Starts the ringing and sets the alarm for the next one. */
class TimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = runAsync {
        TimerController.get(context).sync()
    }
}

/**
 * After a restart (straight away, before the watch is unlocked), an app update or a change of the
 * clock, the alarm has to be set again.
 */
class RestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in restoreActions) return
        runAsync { TimerController.get(context).sync() }
    }

    private companion object {
        val restoreActions = setOf(
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
        )
    }
}

/** Stop and +1 min on the "Time's up" notification: for every finished timer. */
class TimerActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = runAsync {
        val now = System.currentTimeMillis()
        TimerController.get(context).update(now) { s ->
            s.copy(
                timers = s.timers.map {
                    when {
                        !it.isDone(now) -> it
                        intent.action == ACTION_ADD_MINUTE -> it.addMinute(now)
                        else -> it.reset()
                    }
                },
            )
        }
    }

    companion object {
        const val ACTION_STOP = "io.github.iannicholls89.wearmultitimer.STOP_FINISHED"
        const val ACTION_ADD_MINUTE = "io.github.iannicholls89.wearmultitimer.ADD_MINUTE_FINISHED"
    }
}

/** Stops every finished timer: Stop all, and the notification's Stop. */
fun List<TimerItem>.stopFinished(now: Long): List<TimerItem> = map { if (it.isDone(now)) it.reset() else it }
