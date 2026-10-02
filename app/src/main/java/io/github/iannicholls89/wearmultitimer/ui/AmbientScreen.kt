package io.github.iannicholls89.wearmultitimer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import io.github.iannicholls89.wearmultitimer.timer.TimerItem
import io.github.iannicholls89.wearmultitimer.timer.displayName
import io.github.iannicholls89.wearmultitimer.timer.formatDuration
import io.github.iannicholls89.wearmultitimer.timer.sortedForList

/**
 * What stays on screen when the watch dims with the app open, as Google Clock's timer does:
 * black, no filled shapes, and the time left to the minute (the watch redraws about once a
 * minute here). On screens that need it, everything shifts a little each minute against burn-in.
 */
@Composable
fun AmbientScreen(timers: List<TimerItem>, now: Long, burnInProtection: Boolean) {
    val all = timers.filter { it.state != TimerItem.State.RESET }.sortedForList(now)
    val shown = all.take(3)
    val shift = if (burnInProtection) (((now / 60_000) % 3) - 1).toInt() * 4 else 0
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 32.dp)
            .offset(x = shift.dp, y = shift.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (shown.isEmpty()) {
            Text("No timers running", style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1)
        }
        // Name above, time left below: a long name and "Paused 50 min" both fit at a large font.
        shown.forEachIndexed { i, timer ->
            if (i > 0) Spacer(Modifier.height(4.dp))
            Text(
                timer.displayName(),
                style = MaterialTheme.typography.labelMedium,
                color = Color.LightGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                ambientTimeLeft(timer, now),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
            )
        }
        if (all.size > shown.size) {
            Spacer(Modifier.height(4.dp))
            Text("and ${all.size - shown.size} more", style = MaterialTheme.typography.labelSmall, color = Color.LightGray, maxLines = 1)
        }
    }
}

/** To the minute, rounded up: "9 min", "1 h 5 min"; "Paused 9 min"; "Time's up". */
internal fun ambientTimeLeft(timer: TimerItem, now: Long): String {
    if (timer.isDone(now)) return "Time's up"
    val minutes = (timer.remaining(now) + 59_999) / 60_000
    val left = formatDuration(minutes * 60_000)
    return if (timer.state == TimerItem.State.PAUSED) "Paused $left" else left
}
