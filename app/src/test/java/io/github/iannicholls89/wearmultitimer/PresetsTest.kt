package io.github.iannicholls89.wearmultitimer

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import io.github.iannicholls89.wearmultitimer.alarm.TimerController
import io.github.iannicholls89.wearmultitimer.timer.AppState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PresetsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val controller = TimerController.get(context)
    private val vm = TimerViewModel(controller)

    private fun state(): AppState = runBlocking { controller.store.state.first() }

    /** The view model works on the main thread, and saving on DataStore's: wait for both. */
    private fun waitFor(condition: (AppState) -> Boolean): AppState {
        val until = System.currentTimeMillis() + 5_000
        while (true) {
            shadowOf(Looper.getMainLooper()).idle()
            val s = state()
            if (condition(s)) return s
            check(System.currentTimeMillis() < until) { "Timed out: $s" }
            Thread.sleep(20)
        }
    }

    @Before fun clear() = runBlocking {
        controller.update(System.currentTimeMillis()) { AppState() }
        Unit
    }

    @Test fun `a named timer can be kept as a preset, once`() {
        vm.create(600_000, "  Pasta ", saveAsPreset = true)
        var s = waitFor { it.timers.size == 1 }
        assertEquals("Pasta", s.timers.single().name)
        assertEquals(listOf("Pasta" to 600_000L), s.presets.map { it.name to it.durationMs })

        vm.create(600_000, "Pasta", saveAsPreset = true)
        s = waitFor { it.timers.size == 2 }
        assertEquals("the same name and duration aren't saved twice", 1, s.presets.size)
    }

    @Test fun `no preset without a name or without asking`() {
        vm.create(300_000, null, saveAsPreset = true)
        vm.create(300_000, "Eggs", saveAsPreset = false)
        val s = waitFor { it.timers.size == 2 }
        assertEquals(0, s.presets.size)
        assertNull("a blank name is no name", s.timers[0].name)
    }

    @Test fun `a preset starts a running timer with its name, and can be deleted`() {
        vm.create(420_000, "Eggs", saveAsPreset = true)
        val preset = waitFor { it.presets.isNotEmpty() }.presets.single()
        var made = 0L
        vm.startPreset(preset.id) { made = it }
        val s = waitFor { it.timers.size == 2 && made != 0L }
        val t = s.timers.single { it.id == made }
        assertEquals("Eggs", t.name)
        assertEquals(420_000, t.durationMs)
        assertEquals(io.github.iannicholls89.wearmultitimer.timer.TimerItem.State.RUNNING, t.state)

        vm.deletePreset(preset.id)
        assertEquals(0, waitFor { it.presets.isEmpty() }.presets.size)
    }

    @Test fun `renaming, and a blank name goes back to the duration`() {
        vm.create(60_000, "Tea")
        val id = waitFor { it.timers.isNotEmpty() }.timers.single().id
        vm.rename(id, "Green tea")
        waitFor { it.timers.single().name == "Green tea" }
        vm.rename(id, "   ")
        waitFor { it.timers.single().name == null }
    }
}
