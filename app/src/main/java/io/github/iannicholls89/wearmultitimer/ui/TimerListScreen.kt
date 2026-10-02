package io.github.iannicholls89.wearmultitimer.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwipeToReveal
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import io.github.iannicholls89.wearmultitimer.R
import io.github.iannicholls89.wearmultitimer.timer.TimerItem
import io.github.iannicholls89.wearmultitimer.timer.displayName
import io.github.iannicholls89.wearmultitimer.timer.formatCountdown
import io.github.iannicholls89.wearmultitimer.timer.sortedForList

/** Something stopping the alerts from working, with what fixes it. */
data class Notice(val text: String, val onClick: () -> Unit)

/**
 * The app's own updates: [offer] is the button at the top ("Update to 0.4"), when there's one;
 * the foot of the list shows the version installed and [line], and checks again when tapped.
 */
data class UpdateUi(
    val installed: String,
    val line: String,
    val offer: String? = null,
    val busy: Boolean = false,
    val onInstall: () -> Unit = {},
    val onCheck: () -> Unit = {},
)

/**
 * Home: every timer, finished ones first, then the soonest to finish. Tap one to open it;
 * swipe one left to delete it. [timers] is null until the saved ones have been read.
 */
@Composable
fun TimerListScreen(
    timers: List<TimerItem>?,
    now: Long,
    onOpen: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onNew: () -> Unit,
    notices: List<Notice> = emptyList(),
    update: UpdateUi? = null,
) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(onClick = onNew, buttonSize = EdgeButtonSize.Medium) {
                Text("New timer", maxLines = 1)
            }
        },
    ) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ListHeader(Modifier.transformedHeight(this, spec)) { Text("Timers") } }
            update?.offer?.let { offer ->
                item {
                    Button(
                        onClick = update.onInstall,
                        enabled = !update.busy,
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                        icon = { Icon(painterResource(R.drawable.ic_download), contentDescription = null) },
                    ) { Text(offer, maxLines = 2) }
                }
            }
            items(notices) { notice ->
                Button(
                    onClick = notice.onClick,
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) { Text(notice.text, style = MaterialTheme.typography.labelMedium) }
            }
            if (timers?.isEmpty() == true) {
                item {
                    Text(
                        "No timers yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    )
                }
            }
            items(timers.orEmpty().sortedForList(now), key = { it.id }) { timer ->
                SwipeToReveal(
                    primaryAction = {
                        PrimaryActionButton(
                            onClick = { onDelete(timer.id) },
                            icon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
                            text = { Text("Delete") },
                        )
                    },
                    onSwipePrimaryAction = { onDelete(timer.id) },
                    modifier = Modifier.transformedHeight(this, spec),
                ) {
                    TimerRow(timer, now, { onOpen(timer.id) }, SurfaceTransformation(spec))
                }
            }
            update?.let { u ->
                item {
                    Button(
                        onClick = u.onCheck,
                        enabled = !u.busy,
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                        colors = ButtonDefaults.childButtonColors(),
                        secondaryLabel = { Text(u.line, maxLines = 2) },
                    ) { Text("Version ${u.installed}", maxLines = 1) }
                }
            }
        }
    }
}

@Composable
private fun TimerRow(timer: TimerItem, now: Long, onClick: () -> Unit, transformation: SurfaceTransformation) {
    val done = timer.isDone(now)
    val status = when {
        done -> "Time's up"
        timer.state == TimerItem.State.RUNNING -> null
        timer.state == TimerItem.State.PAUSED -> "Paused"
        else -> "Not started"
    }
    val colors = when {
        done -> ButtonDefaults.buttonColors()
        timer.state == TimerItem.State.RUNNING -> ButtonDefaults.filledTonalButtonColors()
        else -> ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
            secondaryContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    // Read through a State: the indicator only redraws when the progress it reads is one.
    val progress by rememberUpdatedState(timer.progress(now))
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = colors,
        transformation = transformation,
        icon = {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(ButtonDefaults.LargeIconSize),
                strokeWidth = 4.dp,
            )
        },
        secondaryLabel = {
            Text(
                listOfNotNull(timer.displayName(), status).joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    ) {
        Text(formatCountdown(timer.remaining(now)), style = MaterialTheme.typography.titleLarge, maxLines = 1)
    }
}
