package io.github.iannicholls89.wearmultitimer.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import io.github.iannicholls89.wearmultitimer.R
import io.github.iannicholls89.wearmultitimer.timer.formatDuration

/**
 * Between picking a duration and starting: an optional name, and whether to keep it as a preset.
 * Start straight away for an unnamed timer, called by its duration.
 */
@Composable
fun NameTimerScreen(
    durationMs: Long,
    name: String?,
    saveAsPreset: Boolean,
    onEditName: () -> Unit,
    onSaveAsPresetChange: (Boolean) -> Unit,
    onStart: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(onClick = onStart, buttonSize = EdgeButtonSize.Medium) {
                Icon(painterResource(R.drawable.ic_play), contentDescription = null)
                Text("Start", maxLines = 1)
            }
        },
    ) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader(Modifier.transformedHeight(this, spec)) {
                    Text(
                        formatDuration(durationMs),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
            item {
                Button(
                    onClick = onEditName,
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    icon = { Icon(painterResource(R.drawable.ic_edit), contentDescription = null) },
                    secondaryLabel = if (name != null) ({ Text("Tap to change", maxLines = 1) }) else null,
                ) { Text(name ?: "Add a name", maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            if (name != null) {
                item {
                    SwitchButton(
                        checked = saveAsPreset,
                        onCheckedChange = onSaveAsPresetChange,
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                    ) { Text("Save as preset", maxLines = 2) }
                }
            }
        }
    }
}
