package io.github.iannicholls89.wearmultitimer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimePicker
import androidx.wear.compose.material3.TimePickerType
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import io.github.iannicholls89.wearmultitimer.R
import io.github.iannicholls89.wearmultitimer.timer.formatDuration
import java.time.LocalTime

private val quickMinutes = listOf(1, 3, 5, 10, 15, 30, 45, 60)

/** As Google Clock: common durations a tap away, and Custom for anything else. */
@Composable
fun NewTimerScreen(onPick: (Long) -> Unit, onCustom: () -> Unit) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ListHeader(Modifier.transformedHeight(this, spec)) { Text("New timer") } }
            quickMinutes.chunked(2).forEach { pair ->
                item {
                    Row(
                        Modifier.fillMaxWidth().transformedHeight(this, spec),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        pair.forEach { minutes ->
                            val ms = minutes * 60_000L
                            Button(
                                onClick = { onPick(ms) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.filledTonalButtonColors(),
                                transformation = SurfaceTransformation(spec),
                            ) {
                                Text(
                                    formatDuration(ms).replace("1 h", "1 hour"),
                                    maxLines = 1,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = onCustom,
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                ) { Text("Custom", maxLines = 1) }
            }
        }
    }
}

/** Hours, minutes and seconds on the Material picker. No clock at the top: it sits on the picker's labels. */
@Composable
fun CustomDurationScreen(initial: LocalTime = LocalTime.of(0, 5, 0), onPick: (Long) -> Unit) {
    ScreenScaffold(timeText = {}) {
        TimePicker(
            initialTime = initial,
            onTimePicked = { t -> onPick(t.toSecondOfDay() * 1000L) },
            timePickerType = TimePickerType.HoursMinutesSeconds24H,
        )
    }
}
