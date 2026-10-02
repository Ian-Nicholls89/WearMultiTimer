package io.github.iannicholls89.wearmultitimer.alarm

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.os.Looper
import android.os.Vibrator
import android.os.VibratorManager
import androidx.test.core.app.ApplicationProvider
import io.github.iannicholls89.wearmultitimer.timer.AppState
import io.github.iannicholls89.wearmultitimer.timer.TimerItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlarmTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val controller = TimerController.get(context)
    private val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val now get() = System.currentTimeMillis()

    @Before fun clear() = runBlocking {
        // As on the watch, where USE_EXACT_ALARM is granted on install.
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(true)
        controller.update(now) { AppState() }
        Unit
    }

    private fun set(vararg timers: TimerItem) = runBlocking { controller.update(now) { AppState(timers = timers.toList()) } }

    @Test fun `starting a timer sets the alarm for its end, as an alarm clock`() {
        val t = TimerItem(id = 1, durationMs = 300_000).start(now)
        set(t, TimerItem(id = 2, durationMs = 900_000).start(now))
        val next = alarms.peekNextScheduledAlarm()
        assertNotNull(next)
        assertEquals(t.endAtMs, next!!.triggerAtTime)
        assertNotNull("set as an alarm clock, so it wakes the watch", next.alarmClockInfo)
    }

    @Test fun `without exact alarms it still sets one`() {
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val t = TimerItem(id = 1, durationMs = 300_000).start(now)
        set(t)
        assertEquals(t.endAtMs, alarms.peekNextScheduledAlarm()?.triggerAtTime)
    }

    @Test fun `pausing the only running timer cancels the alarm`() {
        val t = TimerItem(id = 1, durationMs = 300_000).start(now)
        set(t)
        set(t.pause(now))
        assertNull(alarms.peekNextScheduledAlarm())
    }

    @Test fun `a finished timer starts the ringing`() {
        set(TimerItem(id = 1, durationMs = 1_000).start(now - 5_000))
        val started = shadowOf(context as Application).nextStartedService
        assertEquals(RingService::class.java.name, started?.component?.className)
    }

    @Test fun `stop on the notification resets every finished timer and leaves the others`() {
        val running = TimerItem(id = 2, durationMs = 600_000).start(now)
        set(TimerItem(id = 1, durationMs = 1_000).start(now - 5_000), running)
        val after = runBlocking { controller.update(now) { s -> s.copy(timers = s.timers.stopFinished(now)) } }
        assertEquals(TimerItem.State.RESET, after.timers[0].state)
        assertEquals(running, after.timers[1])
    }

    @Test fun `the ringing service buzzes until the timer is stopped, then stops itself`() {
        val t = TimerItem(id = 1, durationMs = 1_000).start(now - 5_000)
        set(t)
        val service = Robolectric.buildService(RingService::class.java).create().startCommand(0, 1)
        val vibrator = shadowOf(context.getSystemService(VibratorManager::class.java).defaultVibrator)
        assertTrue(vibrator.isVibrating)
        assertNotNull(shadowOf(service.get()).lastForegroundNotification)

        set(t.reset())
        waitFor { shadowOf(service.get()).isStoppedBySelf }
        assertFalse(vibrator.isVibrating)
    }

    /** DataStore hands the change over on its own thread; give the main looper a moment to see it. */
    private fun waitFor(condition: () -> Boolean) {
        val until = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < until) { "Timed out" }
            Thread.sleep(20)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }
}
