package io.github.iannicholls89.wearmultitimer.ui

import io.github.iannicholls89.wearmultitimer.timer.TimerItem
import org.junit.Assert.assertEquals
import org.junit.Test

class AmbientTest {
    private val t0 = 1_000_000L

    @Test fun `time left to the minute, rounded up`() {
        val t = TimerItem(id = 1, durationMs = 10 * 60_000).start(t0)
        assertEquals("10 min", ambientTimeLeft(t, t0))
        assertEquals("10 min", ambientTimeLeft(t, t0 + 1_000))
        assertEquals("9 min", ambientTimeLeft(t, t0 + 60_000))
        assertEquals("1 min", ambientTimeLeft(t, t0 + 10 * 60_000 - 1))
        val long = TimerItem(id = 2, durationMs = 65 * 60_000).start(t0)
        assertEquals("1 h 5 min", ambientTimeLeft(long, t0))
    }

    @Test fun `paused and finished say so`() {
        val t = TimerItem(id = 1, durationMs = 10 * 60_000).start(t0)
        assertEquals("Paused 8 min", ambientTimeLeft(t.pause(t0 + 2 * 60_000), t0 + 50 * 60_000))
        assertEquals("Time's up", ambientTimeLeft(t, t0 + 11 * 60_000))
    }
}
