package io.github.iannicholls89.wearmultitimer.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults

/** The ring's gap at the top, for the clock: 25° either side of 12 o'clock (angles from 3 o'clock). */
private const val START_ANGLE = 270f + 25f
private const val END_ANGLE = 270f - 25f

/**
 * The ring round a timer's screen, leaving the top for the clock. Running, it drains with the
 * time left; once the time is up it is all red, pulsing.
 */
@Composable
fun TimerRing(progress: Float, finished: Boolean, modifier: Modifier = Modifier) {
    // Read through a State: the indicator only redraws when the progress it reads is one.
    val shown by rememberUpdatedState(if (finished) 1f else progress)
    val pulse = if (finished) {
        val transition = rememberInfiniteTransition(label = "time's up")
        val alpha by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "pulse",
        )
        alpha
    } else {
        1f
    }
    CircularProgressIndicator(
        progress = { shown },
        modifier = modifier.fillMaxSize().padding(3.dp).alpha(pulse),
        startAngle = START_ANGLE,
        endAngle = END_ANGLE,
        strokeWidth = 5.dp,
        colors = if (finished) {
            ProgressIndicatorDefaults.colors(indicatorColor = MaterialTheme.colorScheme.error)
        } else {
            ProgressIndicatorDefaults.colors()
        },
    )
}
