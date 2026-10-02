package io.github.iannicholls89.wearmultitimer.alarm

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.media.AudioManager
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
import org.robolectric.shadows.ShadowMediaPlayer
import io.github.iannicholls89.wearmultitimer.RingActivity

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

    @Test fun `a running timer shows at the foot of the watch face, and goes when paused`() {
        val nm = shadowOf(context.getSystemService(android.app.NotificationManager::class.java))
        val pasta = TimerItem(id = 1, name = "Pasta", durationMs = 600_000).start(now)
        val tea = TimerItem(id = 2, name = "Tea", durationMs = 240_000).start(now)
        set(pasta, tea)
        val shown = nm.getNotification(Notifications.RUNNING_ID)
        assertNotNull(shown)
        assertTrue("ongoing", shown.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0)
        // The soonest first, and how many more.
        assertEquals("Tea +1", shown.extras.getString(android.app.Notification.EXTRA_TITLE))
        // The Ongoing Activity travels in the notification's extras (the test's notification
        // manager can't hand it back the way the watch does).
        assertTrue("an Ongoing Activity", shown.extras.keySet().any { it.startsWith("android.wearable.ongoingactivities") })

        set(pasta.pause(now), tea.pause(now))
        assertNull(nm.getNotification(Notifications.RUNNING_ID))
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

    @Test fun `the ringing buzzes, chimes and opens the alert until the timer is stopped`() {
        ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo(1_400, 0) }
        // The watch on vibrate, as watches usually are: the chime still plays, as Google Clock's does.
        context.getSystemService(AudioManager::class.java).ringerMode = AudioManager.RINGER_MODE_VIBRATE
        val t = TimerItem(id = 1, durationMs = 1_000).start(now - 5_000)
        set(t)
        val service = Robolectric.buildService(RingService::class.java).create().startCommand(0, 1)
        val vibrator = shadowOf(context.getSystemService(VibratorManager::class.java).defaultVibrator)
        assertTrue(vibrator.isVibrating)
        assertTrue(service.get().isChiming)
        assertNotNull(shadowOf(service.get()).lastForegroundNotification)
        assertEquals(
            RingActivity::class.java.name,
            shadowOf(service.get()).nextStartedActivity?.component?.className,
        )

        set(t.reset())
        waitFor { shadowOf(service.get()).isStoppedBySelf }
        assertFalse(vibrator.isVibrating)
        assertFalse(service.get().isChiming)
    }

    @Test fun `silent mode keeps the chime quiet but still buzzes`() {
        ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo(1_400, 0) }
        context.getSystemService(AudioManager::class.java).ringerMode = AudioManager.RINGER_MODE_SILENT
        set(TimerItem(id = 1, durationMs = 1_000).start(now - 5_000))
        val service = Robolectric.buildService(RingService::class.java).create().startCommand(0, 1)
        assertTrue(shadowOf(context.getSystemService(VibratorManager::class.java).defaultVibrator).isVibrating)
        assertFalse(service.get().isChiming)
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
