package io.github.iannicholls89.wearmultitimer.ui

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
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import androidx.wear.compose.material3.TextButtonDefaults
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import io.github.iannicholls89.wearmultitimer.R
import io.github.iannicholls89.wearmultitimer.timer.TimerItem
import io.github.iannicholls89.wearmultitimer.timer.displayName
import io.github.iannicholls89.wearmultitimer.timer.formatCountdown

/** The alert when timers run out: one fills the screen; several are listed, with Stop all at the foot. */
@Composable
fun RingScreen(
    done: List<TimerItem>,
    now: Long,
    onStop: (Long) -> Unit,
    onAddMinute: (Long) -> Unit,
    onStopAll: () -> Unit,
) {
    if (done.size == 1) SingleRing(done[0], now, onStop, onAddMinute)
    else ManyRing(done, now, onStop, onAddMinute, onStopAll)
}

@Composable
private fun SingleRing(timer: TimerItem, now: Long, onStop: (Long) -> Unit, onAddMinute: (Long) -> Unit) {
    ScreenScaffold(timeText = {}) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "Time's up",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                timer.displayName(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.85f),
            )
            Text(
                formatCountdown(timer.remaining(now)),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledIconButton(
                    onClick = { onStop(timer.id) },
                    modifier = Modifier.size(IconButtonDefaults.LargeButtonSize),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_stop),
                        contentDescription = "Stop",
                        modifier = Modifier.size(IconButtonDefaults.LargeIconSize),
                    )
                }
                TextButton(
                    onClick = { onAddMinute(timer.id) },
                    modifier = Modifier.size(IconButtonDefaults.LargeButtonSize),
                    colors = TextButtonDefaults.filledTonalTextButtonColors(),
                ) { Text("+1:00", maxLines = 1) }
            }
        }
    }
}

@Composable
private fun ManyRing(
    done: List<TimerItem>,
    now: Long,
    onStop: (Long) -> Unit,
    onAddMinute: (Long) -> Unit,
    onStopAll: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(onClick = onStopAll, buttonSize = EdgeButtonSize.Medium) { Text("Stop all", maxLines = 1) }
        },
    ) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ListHeader(Modifier.transformedHeight(this, spec)) { Text("Time's up") } }
            items(done, key = { it.id }) { timer ->
                Card(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                timer.displayName(),
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                formatCountdown(timer.remaining(now)),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1,
                            )
                        }
                        Box {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = { onAddMinute(timer.id) },
                                    colors = TextButtonDefaults.filledTonalTextButtonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    ),
                                ) { Text("+1:00", style = MaterialTheme.typography.labelMedium, maxLines = 1) }
                                FilledIconButton(onClick = { onStop(timer.id) }) {
                                    Icon(painterResource(R.drawable.ic_stop), contentDescription = "Stop ${timer.displayName()}")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
