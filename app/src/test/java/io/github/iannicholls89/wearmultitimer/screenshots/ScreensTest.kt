package io.github.iannicholls89.wearmultitimer.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.wear.compose.material3.AppScaffold
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.iannicholls89.wearmultitimer.timer.TimerItem
import io.github.iannicholls89.wearmultitimer.ui.CustomDurationScreen
import io.github.iannicholls89.wearmultitimer.ui.NameTimerScreen
import io.github.iannicholls89.wearmultitimer.ui.NewTimerScreen
import io.github.iannicholls89.wearmultitimer.timer.Preset
import io.github.iannicholls89.wearmultitimer.ui.Notice
import io.github.iannicholls89.wearmultitimer.ui.RingScreen
import io.github.iannicholls89.wearmultitimer.ui.TimerListScreen
import io.github.iannicholls89.wearmultitimer.ui.TimerScreen
import io.github.iannicholls89.wearmultitimer.ui.TimerTheme
import io.github.iannicholls89.wearmultitimer.ui.UpdateUi
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

    /** 1.15 as the user's watch; -PfontScale=1.3 renders the largest text, into build/screenshots-1.3. */
    private val fontScale = System.getProperty("fontScale")?.toFloatOrNull() ?: 1.15f

    private fun shoot(name: String, content: @Composable () -> Unit) {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent { TimerTheme { AppScaffold { content() } } }
        compose.waitForIdle()
        val dir = if (fontScale == 1.15f) "build/screenshots" else "build/screenshots-$fontScale"
        compose.onRoot().captureRoboImage("$dir/$name.png")
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

    @Test fun update() = shoot("13-list-update") {
        TimerListScreen(
            listOf(pasta, laundry), now, {}, {}, {},
            update = UpdateUi(installed = "0.3.3", line = "0.4 is out", offer = "Update to 0.4"),
        )
    }

    @Test fun updateFooter() = shoot("14-list-version") {
        TimerListScreen(
            emptyList(), now, {}, {}, {},
            update = UpdateUi(installed = "0.3.3", line = "Up to date"),
        )
    }

    @Test fun newWithPresets() = shoot("15-new-presets") {
        NewTimerScreen({}, {}, presets = listOf(Preset(1, "Pasta", 10 * min), Preset(2, "Boiled eggs", 7 * min)))
    }

    @Test fun nameStep() = shoot("16-name-step") { NameTimerScreen(10 * min, null, false, {}, {}, {}) }

    @Test fun nameStepNamed() = shoot("17-name-step-named") { NameTimerScreen(10 * min, "Pasta", true, {}, {}, {}) }

    @Test fun ambient() = shoot("18-dimmed") {
        io.github.iannicholls89.wearmultitimer.ui.AmbientScreen(listOf(pasta, laundry, bread, tea), now, burnInProtection = false)
    }

    @Test fun reset() = shoot("8-reset") { TimerScreen(unnamed, now, {}, {}, {}, {}) }
}
