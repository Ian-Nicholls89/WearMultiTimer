package io.github.iannicholls89.wearmultitimer

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import io.github.iannicholls89.wearmultitimer.timer.AppState
import io.github.iannicholls89.wearmultitimer.timer.TimerItem
import io.github.iannicholls89.wearmultitimer.timer.TimerStore
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The whole app with a made-up clock: the countdown must move on, on the list and on a timer's own screen. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w228dp-h228dp-round-xhdpi")
class CountdownTest {
    @get:Rule val compose = createComposeRule()

    private var fakeNow = 1_700_000_000_000L

    private fun seed() = runBlocking {
        val store = TimerStore.get(ApplicationProvider.getApplicationContext())
        store.update {
            AppState(timers = listOf(TimerItem(id = 1, name = "Pasta", durationMs = 600_000).start(fakeNow)), nextId = 2)
        }
    }

    private fun tick(ms: Long) {
        fakeNow += ms
        compose.mainClock.advanceTimeBy(ms + 50)
    }

    @Test fun listCountsDown() {
        seed()
        compose.setContent { WearMultiTimerApp(clock = { fakeNow }, checkForUpdates = false) }
        compose.waitUntilAtLeastOneExists(hasText("10:00"), 5_000)
        tick(1_000)
        compose.waitUntilAtLeastOneExists(hasText("9:59"), 5_000)
        tick(60_000)
        compose.waitUntilAtLeastOneExists(hasText("8:59"), 5_000)
    }

    @Test fun timerScreenCountsDown() {
        seed()
        compose.setContent { WearMultiTimerApp(clock = { fakeNow }, checkForUpdates = false) }
        compose.waitUntilAtLeastOneExists(hasText("10:00"), 5_000)
        compose.onNodeWithText("10:00").performClick()
        compose.waitUntilAtLeastOneExists(hasText("+1:00"), 5_000)
        tick(2_000)
        compose.waitUntilAtLeastOneExists(hasText("9:58"), 5_000)
        tick(10 * 60_000)
        compose.waitUntilAtLeastOneExists(hasText("-0:02"), 5_000)
    }

    @Test fun newTimerGoesThroughTheNameStep() {
        runBlocking { TimerStore.get(ApplicationProvider.getApplicationContext()).update { AppState() } }
        compose.setContent { WearMultiTimerApp(clock = { fakeNow }, checkForUpdates = false) }
        compose.waitUntilAtLeastOneExists(hasText("New timer"), 5_000)
        // The edge button grows into view at the end of the list, as in Google's own apps.
        compose.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        compose.waitForIdle()
        compose.onNodeWithText("New timer").performClick()
        compose.waitUntilAtLeastOneExists(hasText("5 min"), 5_000)
        compose.onNodeWithText("5 min").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Add a name"), 5_000)
        compose.onNodeWithText("Start").performClick()
        compose.waitUntilAtLeastOneExists(hasText("+1:00"), 5_000)
        tick(1_000)
        compose.waitUntilAtLeastOneExists(hasText("4:59"), 5_000)
    }
}
