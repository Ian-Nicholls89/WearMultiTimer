package io.github.iannicholls89.wearmultitimer

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.AppScaffold
import io.github.iannicholls89.wearmultitimer.alarm.TimerController
import io.github.iannicholls89.wearmultitimer.alarm.stopFinished
import io.github.iannicholls89.wearmultitimer.timer.TimerItem
import io.github.iannicholls89.wearmultitimer.ui.RingScreen
import io.github.iannicholls89.wearmultitimer.ui.TimerTheme
import io.github.iannicholls89.wearmultitimer.ui.rememberNow
import kotlinx.coroutines.flow.map

/** The full-screen "Time's up", over the watch face. Closes itself once no timer is finished. */
class RingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { RingApp(onNothingLeft = ::finish) }
    }
}

@Composable
private fun RingApp(onNothingLeft: () -> Unit) {
    val context = LocalContext.current
    val controller = remember { TimerController.get(context) }
    val timers by remember { controller.store.state.map { it.timers } }.collectAsStateWithLifecycle(null)
    val now = rememberNow(timers.orEmpty())
    val done = timers.orEmpty().filter { it.isDone(now) }.sortedBy { it.endAtMs }

    if (timers != null && done.isEmpty()) LaunchedEffect(Unit) { onNothingLeft() }

    fun change(id: Long?, edit: (TimerItem, Long) -> TimerItem) = controller.launch {
        val at = System.currentTimeMillis()
        update(at) { s -> s.copy(timers = s.timers.map { if ((id == null || it.id == id) && it.isDone(at)) edit(it, at) else it }) }
    }

    TimerTheme {
        AppScaffold {
            if (done.isNotEmpty()) {
                RingScreen(
                    done = done,
                    now = now,
                    onStop = { id -> change(id) { t, _ -> t.reset() } },
                    onAddMinute = { id -> change(id) { t, at -> t.addMinute(at) } },
                    onStopAll = {
                        controller.launch {
                            val at = System.currentTimeMillis()
                            update(at) { s -> s.copy(timers = s.timers.stopFinished(at)) }
                        }
                    },
                )
            }
        }
    }
}
