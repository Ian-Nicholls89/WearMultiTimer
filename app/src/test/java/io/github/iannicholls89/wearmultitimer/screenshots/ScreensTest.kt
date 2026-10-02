package io.github.iannicholls89.wearmultitimer.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.wear.compose.material3.AppScaffold
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.iannicholls89.wearmultitimer.timer.TimerItem
import io.github.iannicholls89.wearmultitimer.ui.CustomDurationScreen
import io.github.iannicholls89.wearmultitimer.ui.NewTimerScreen
import io.github.iannicholls89.wearmultitimer.ui.Notice
import io.github.iannicholls89.wearmultitimer.ui.RingScreen
import io.github.iannicholls89.wearmultitimer.ui.TimerListScreen
import io.github.iannicholls89.wearmultitimer.ui.TimerScreen
import io.github.iannicholls89.wearmultitimer.ui.TimerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The screens with made-up timers, round and at a large font, as on the user's watch. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w228dp-h228dp-round-xhdpi", application = android.app.Application::class)
class ScreensTest {

    @get:Rule val compose = createComposeRule()

    private val now = 1_700_000_000_000L
    private val min = 60_000L

    private val pasta = TimerItem(id = 1, name = "Pasta", durationMs = 10 * min).start(now - 2 * min - 19_000)
    private val laundry = TimerItem(id = 2, name = "Laundry", durationMs = 45 * min).start(now - 5 * min)
    private val tea = TimerItem(id = 3, name = "Tea", durationMs = 4 * min).start(now - 4 * min - 12_000)
    private val bread = TimerItem(id = 4, name = "Bread proving", durationMs = 90 * min).start(now - 60 * min).pause(now - 20 * min)
    private val unnamed = TimerItem(id = 5, durationMs = 5 * min)

    private fun shoot(name: String, content: @Composable () -> Unit) {
        RuntimeEnvironment.setFontScale(1.15f)
        compose.setContent { TimerTheme { AppScaffold { content() } } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test fun list() = shoot("1-list") {
        TimerListScreen(listOf(pasta, laundry, tea, bread, unnamed), now, {}, {}, {})
    }

    @Test fun listEmpty() = shoot("2-list-empty") { TimerListScreen(emptyList(), now, {}, {}, {}) }

    @Test fun newTimer() = shoot("3-new-timer") { NewTimerScreen({}, {}) }

    @Test fun custom() = shoot("4-custom") { CustomDurationScreen {} }

    @Test fun running() = shoot("5-running") { TimerScreen(pasta, now, {}, {}, {}, {}) }

    @Test fun paused() = shoot("6-paused-hours") { TimerScreen(bread, now, {}, {}, {}, {}) }

    @Test fun done() = shoot("7-times-up") { TimerScreen(tea, now, {}, {}, {}, {}) }

    @Test fun hours() = shoot("9-running-hours") {
        TimerScreen(TimerItem(id = 6, name = "Slow cooker", durationMs = 4 * 60 * min).start(now - 37 * min - 30_000), now, {}, {}, {}, {})
    }

    @Test fun ringOne() = shoot("10-ring-one") { RingScreen(listOf(tea), now, {}, {}, {}) }

    @Test fun ringMany() = shoot("11-ring-many") {
        val eggs = TimerItem(id = 7, name = "Eggs", durationMs = 7 * min).start(now - 7 * min - 3_000)
        RingScreen(listOf(tea, eggs), now, {}, {}, {})
    }

    @Test fun notice() = shoot("12-list-notice") {
        TimerListScreen(
            listOf(pasta), now, {}, {}, {},
            notices = listOf(Notice("Notifications off: timers can't alert you. Tap to fix.") {}),
        )
    }

    @Test fun reset() = shoot("8-reset") { TimerScreen(unnamed, now, {}, {}, {}, {}) }
}
