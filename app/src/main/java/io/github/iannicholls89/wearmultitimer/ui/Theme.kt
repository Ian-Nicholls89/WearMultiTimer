package io.github.iannicholls89.wearmultitimer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.dynamicColorScheme
import io.github.iannicholls89.wearmultitimer.timer.TimerItem
import io.github.iannicholls89.wearmultitimer.timer.millisToNextTick
import kotlinx.coroutines.delay

/** The watch's own colours where it has them (as Google's apps use), else Material's defaults. */
@Composable
fun TimerTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = remember(context) { dynamicColorScheme(context) ?: ColorScheme() }
    MaterialTheme(colorScheme = colors, content = content)
}

/** The current time, updated whenever a running timer's seconds turn over, while the app is on screen. */
@Composable
fun rememberNow(timers: List<TimerItem>, clock: () -> Long = System::currentTimeMillis): Long {
    var now by remember { mutableLongStateOf(clock()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(timers, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = clock()
                val wait = timers.millisToNextTick(now) ?: break
                delay(wait.coerceAtLeast(16))
            }
        }
    }
    return now
}
