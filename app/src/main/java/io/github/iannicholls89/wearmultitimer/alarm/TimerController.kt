package io.github.iannicholls89.wearmultitimer.alarm

import android.annotation.SuppressLint
import android.content.Context
import io.github.iannicholls89.wearmultitimer.timer.AppState
import io.github.iannicholls89.wearmultitimer.timer.TimerStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Every change to the timers goes through here, so the alarm and the ringing always follow them:
 * after each change the alarm is set for the next timer to finish, and the ringing is started or
 * stopped to match. Used by the screens, the alarm, the notification's buttons and the restart.
 */
class TimerController private constructor(
    // The application context, which lives as long as the app does.
    private val context: Context,
) {
    val store: TimerStore = TimerStore.get(context)

    /** Outlives any one screen, so a change made as the ring screen closes still finishes. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    suspend fun update(now: Long, transform: (AppState) -> AppState): AppState {
        val state = store.update(transform)
        sync(state, now)
        return state
    }

    fun launch(block: suspend TimerController.() -> Unit) {
        scope.launch { block() }
    }

    /** Re-set the alarm and the ringing from what's saved - after a restart, an update or the alarm itself. */
    suspend fun sync(now: Long = System.currentTimeMillis()) = sync(store.state.first(), now)

    fun sync(state: AppState, now: Long) {
        AlarmScheduler.set(context, state.timers)
        if (state.timers.any { it.isRinging(now) }) {
            RingService.start(context)
        } else {
            // Nothing buzzing: any timers that are done wait in a silent notification instead.
            Notifications.showFinished(context, state.timers, now)
        }
    }

    companion object {
        @SuppressLint("StaticFieldLeak") // Holds only the application context.
        @Volatile private var instance: TimerController? = null

        /** One per app. (Tests start a fresh app each time, so it follows the app it's given.) */
        fun get(context: Context): TimerController {
            val app = context.applicationContext
            instance?.takeIf { it.context === app }?.let { return it }
            return synchronized(this) {
                instance?.takeIf { it.context === app } ?: TimerController(app).also { instance = it }
            }
        }
    }
}
