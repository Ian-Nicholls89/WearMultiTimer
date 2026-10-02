package io.github.iannicholls89.wearmultitimer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import androidx.wear.compose.material3.TextButtonDefaults
import io.github.iannicholls89.wearmultitimer.R
import io.github.iannicholls89.wearmultitimer.timer.TimerItem
import io.github.iannicholls89.wearmultitimer.timer.displayName
import io.github.iannicholls89.wearmultitimer.timer.displaySeconds
import io.github.iannicholls89.wearmultitimer.timer.formatCountdown
import io.github.iannicholls89.wearmultitimer.timer.spokenStatus
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick

/**
 * One timer, as Google Clock shows it: a ring round the edge draining as time passes, the time
 * left in the middle, and below it reset, start/pause and +1 min. Once reset, the left-hand
 * button deletes the timer instead; once it has run out, the middle one stops it.
 */
@Composable
fun TimerScreen(
    timer: TimerItem,
    now: Long,
    onStartPause: () -> Unit,
    onAddMinute: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit = {},
) {
    val done = timer.isDone(now)
    val running = timer.state == TimerItem.State.RUNNING
    val isReset = timer.state == TimerItem.State.RESET
    val remaining = timer.remaining(now)
    // The clock shows in the gap at the top of the ring, as in Google Clock.
    ScreenScaffold {
        Box(Modifier.fillMaxSize()) {
            TimerRing(timer.progress(now), finished = done)
            Column(
                Modifier.fillMaxSize().padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // Read out as one: "Pasta. Timer running, 9 minutes 41 seconds left."
                Column(
                    Modifier.clearAndSetSemantics {
                        contentDescription = timer.spokenStatus(now)
                        onClick(label = "Rename") { onRename(); true }
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        timer.displayName(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        // Tap the name to rename the timer.
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .clickable(onClickLabel = "Rename", onClick = onRename),
                    )
                    Text(
                        formatCountdown(remaining),
                        // Hours need the room: 1:02:03 is three digits wider than 9:41.
                        style = if (kotlin.math.abs(displaySeconds(remaining)) >= 3600) MaterialTheme.typography.displayMedium
                        else MaterialTheme.typography.displayLarge,
                        color = if (done) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Text(
                        when {
                            done -> "Time's up"
                            timer.state == TimerItem.State.PAUSED -> "Paused"
                            else -> ""
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalIconButton(onClick = if (isReset) onDelete else onReset) {
                        Icon(
                            painterResource(if (isReset) R.drawable.ic_delete else R.drawable.ic_reset),
                            contentDescription = if (isReset) "Delete" else "Reset",
                        )
                    }
                    FilledIconButton(
                        onClick = if (done) onReset else onStartPause,
                        modifier = Modifier.size(IconButtonDefaults.LargeButtonSize),
                    ) {
                        Icon(
                            painterResource(
                                when {
                                    done -> R.drawable.ic_stop
                                    running -> R.drawable.ic_pause
                                    else -> R.drawable.ic_play
                                },
                            ),
                            contentDescription = when {
                                done -> "Stop"
                                running -> "Pause"
                                else -> "Start"
                            },
                            modifier = Modifier.size(IconButtonDefaults.LargeIconSize),
                        )
                    }
                    TextButton(
                        onClick = onAddMinute,
                        enabled = !isReset,
                        colors = TextButtonDefaults.filledTonalTextButtonColors(),
                    ) { Text("+1:00", maxLines = 1) }
                }
            }
        }
    }
}
