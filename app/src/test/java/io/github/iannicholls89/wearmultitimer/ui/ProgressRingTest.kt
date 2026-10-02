package io.github.iannicholls89.wearmultitimer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.wear.compose.material3.AppScaffold
import io.github.iannicholls89.wearmultitimer.timer.TimerItem
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The ring round a timer's screen follows the timer as it changes, not just as it first opened:
 * at 9 o'clock it is unlit with a quarter to go, and lit again once restarted.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w228dp-h228dp-round-xhdpi", application = android.app.Application::class)
class ProgressRingTest {
    @get:Rule val compose = createComposeRule()

    /** How bright the ring is at 9 o'clock: the middle of its stroke, 7dp in from the left edge. */
    private fun ringAtNine(): Float {
        val image = compose.onRoot().captureToImage().toPixelMap()
        val dp = image.width / 228f
        return image[(7 * dp).toInt(), image.height / 2].luminance()
    }

    @Test fun ringRefillsWhenRestarted() {
        val t0 = 1_700_000_000_000L
        var timer by mutableStateOf(TimerItem(id = 1, name = "Tea", durationMs = 60_000).start(t0))
        var now by mutableStateOf(t0 + 1_000)
        compose.setContent { TimerTheme { AppScaffold { TimerScreen(timer, now, {}, {}, {}, {}) } } }
        compose.waitForIdle()
        val full = ringAtNine()

        now = t0 + 45_000
        compose.waitForIdle()
        val quarterLeft = ringAtNine()
        assertTrue("unlit with a quarter to go ($quarterLeft vs $full)", quarterLeft < full - 0.3f)

        timer = timer.reset().start(now)
        now += 1_000
        compose.waitForIdle()
        val restarted = ringAtNine()
        assertTrue("lit again once restarted ($restarted vs $full)", restarted > full - 0.05f)
    }
}
