package io.github.iannicholls89.wearmultitimer

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.junit.Assert.assertTrue
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

    /** What the screen reader says for a timer, which is how the countdown is checked. */
    private fun said(text: String) = hasContentDescription(text, substring = true)

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
        compose.waitUntilAtLeastOneExists(said("10 minutes left"), 5_000)
        tick(1_000)
        compose.waitUntilAtLeastOneExists(said("9 minutes 59 seconds left"), 5_000)
        tick(60_000)
        compose.waitUntilAtLeastOneExists(said("8 minutes 59 seconds left"), 5_000)
    }

    @Test fun timerScreenCountsDown() {
        seed()
        compose.setContent { WearMultiTimerApp(clock = { fakeNow }, checkForUpdates = false) }
        compose.waitUntilAtLeastOneExists(said("10 minutes left"), 5_000)
        compose.onNode(said("10 minutes left")).performClick()
        compose.waitUntilAtLeastOneExists(hasText("+1:00"), 5_000)
        tick(2_000)
        compose.waitUntilAtLeastOneExists(said("9 minutes 58 seconds left"), 5_000)
        tick(10 * 60_000)
        compose.waitUntilAtLeastOneExists(said("2 seconds over"), 5_000)
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
        compose.waitUntilAtLeastOneExists(said("4 minutes 59 seconds left"), 5_000)
    }

    @Test fun dimmingShowsTheDimScreenAndWakingComesBack() {
        seed()
        var ambient by androidx.compose.runtime.mutableStateOf<Boolean?>(null)
        compose.setContent {
            WearMultiTimerApp(clock = { fakeNow }, checkForUpdates = false, ambient = ambient, ambientNow = fakeNow)
        }
        compose.waitUntilAtLeastOneExists(said("10 minutes left"), 5_000)
        compose.onNode(said("10 minutes left")).performClick()
        compose.waitUntilAtLeastOneExists(hasText("+1:00"), 5_000)

        ambient = false
        compose.waitUntilAtLeastOneExists(hasText("10 min"), 5_000)
        compose.onNodeWithText("Pasta").assertExists()
        assertTrue("the buttons are gone while dimmed", compose.onAllNodesWithText("+1:00").fetchSemanticsNodes().isEmpty())

        ambient = null
        tick(1_000)
        compose.waitUntilAtLeastOneExists(said("9 minutes 59 seconds left"), 5_000)
        compose.onNodeWithText("+1:00").assertExists()
    }
}
