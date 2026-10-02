package io.github.iannicholls89.wearmultitimer.timer

import io.github.iannicholls89.wearmultitimer.timer.TimerItem.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerItemTest {
    private val t0 = 1_000_000L
    private val tenMin = 10 * 60_000L
    private fun fresh() = TimerItem(id = 1, durationMs = tenMin, createdAtMs = t0)

    @Test fun `a new timer waits at its full duration`() {
        val t = fresh()
        assertEquals(State.RESET, t.state)
        assertEquals(tenMin, t.remaining(t0 + 99_999))
        assertEquals(1f, t.progress(t0), 0f)
    }

    @Test fun `running counts down from the end time`() {
        val t = fresh().start(t0)
        assertEquals(t0 + tenMin, t.endAtMs)
        assertEquals(tenMin - 90_000, t.remaining(t0 + 90_000))
        assertEquals(0.85f, t.progress(t0 + 90_000), 0.0001f)
        assertFalse(t.isDone(t0 + tenMin - 1))
        assertTrue(t.isDone(t0 + tenMin))
    }

    @Test fun `starting a running timer changes nothing`() {
        val t = fresh().start(t0)
        assertSame(t, t.start(t0 + 5_000))
    }

    @Test fun `pause keeps the time left and resume carries on from it`() {
        val paused = fresh().start(t0).pause(t0 + 60_000)
        assertEquals(State.PAUSED, paused.state)
        assertNull(paused.endAtMs)
        // Time passes while paused; nothing comes off.
        assertEquals(tenMin - 60_000, paused.remaining(t0 + 3_600_000))
        val resumed = paused.start(t0 + 3_600_000)
        assertEquals(t0 + 3_600_000 + tenMin - 60_000, resumed.endAtMs)
    }

    @Test fun `a finished timer can't be paused`() {
        val t = fresh().start(t0)
        assertSame(t, t.pause(t0 + tenMin + 5_000))
    }

    @Test fun `plus one minute while running moves the end and grows the ring`() {
        val t = fresh().start(t0).addMinute(t0 + 30_000)
        assertEquals(t0 + tenMin + 60_000, t.endAtMs)
        assertEquals(tenMin + 60_000, t.totalMs)
        assertEquals(tenMin, t.durationMs)
    }

    @Test fun `plus one minute while paused adds to the time left`() {
        val t = fresh().start(t0).pause(t0 + 60_000).addMinute(t0 + 120_000)
        assertEquals(tenMin, t.remainingMs)
        assertEquals(tenMin + 60_000, t.totalMs)
    }

    @Test fun `plus one minute after time's up starts a fresh minute`() {
        val t = fresh().start(t0).addMinute(t0 + tenMin + 20_000)
        assertEquals(t0 + tenMin + 20_000 + 60_000, t.endAtMs)
        assertEquals(60_000, t.totalMs)
        assertFalse(t.isDone(t0 + tenMin + 20_000))
    }

    @Test fun `plus one minute does nothing to a reset timer`() {
        val t = fresh()
        assertSame(t, t.addMinute(t0))
    }

    @Test fun `overtime is negative`() {
        val t = fresh().start(t0)
        assertEquals(-5_000, t.remaining(t0 + tenMin + 5_000))
        assertEquals(0f, t.progress(t0 + tenMin + 5_000), 0f)
    }

    @Test fun `reset goes back to the duration as set`() {
        val t = fresh().start(t0).addMinute(t0).pause(t0 + 1_000).reset()
        assertEquals(State.RESET, t.state)
        assertEquals(tenMin, t.remainingMs)
        assertEquals(tenMin, t.totalMs)
        assertNull(t.endAtMs)
    }

    @Test fun `the list puts finished first, then soonest, then the rest in order made`() {
        val now = t0 + 100_000
        val done = TimerItem(id = 5, durationMs = 60_000).start(t0)
        val late = TimerItem(id = 2, durationMs = tenMin * 2).start(t0)
        val soon = TimerItem(id = 3, durationMs = tenMin).start(t0)
        val paused = TimerItem(id = 1, durationMs = tenMin).start(t0).pause(t0 + 1)
        val reset = TimerItem(id = 4, durationMs = tenMin)
        val order = listOf(reset, paused, late, soon, done).sortedForList(now).map { it.id }
        assertEquals(listOf(5L, 3L, 2L, 1L, 4L), order)
    }

    @Test fun `the next tick is when a running timer's seconds turn over`() {
        val a = TimerItem(id = 1, durationMs = 10_250).start(t0)  // turns over at +250, +1250…
        val b = TimerItem(id = 2, durationMs = 10_600).start(t0)  // at +600, +1600…
        assertEquals(250L, listOf(a, b).millisToNextTick(t0))
        assertEquals(350L, listOf(a, b).millisToNextTick(t0 + 250))
        assertEquals(1000L, listOf(a).millisToNextTick(t0 + 250))
        // Overtime keeps ticking.
        assertEquals(250L, listOf(a).millisToNextTick(t0 + 20_000))
        assertNull(listOf(a.pause(t0 + 1), TimerItem(id = 3, durationMs = 1)).millisToNextTick(t0))
    }
}

class TimerAlarmLogicTest {
    private val t0 = 1_000_000L

    @Test fun `a finished timer rings for two minutes, then waits quietly`() {
        val t = TimerItem(id = 1, durationMs = 60_000).start(t0)
        assertFalse(t.isRinging(t0 + 59_999))
        assertTrue(t.isRinging(t0 + 60_000))
        assertTrue(t.isRinging(t0 + 60_000 + TimerItem.RING_FOR_MS - 1))
        assertFalse(t.isRinging(t0 + 60_000 + TimerItem.RING_FOR_MS))
        assertTrue(t.isDone(t0 + 60_000 + TimerItem.RING_FOR_MS))
    }

    @Test fun `plus one minute or stop ends the ringing`() {
        val t = TimerItem(id = 1, durationMs = 60_000).start(t0)
        val at = t0 + 70_000
        assertFalse(t.addMinute(at).isRinging(at))
        assertFalse(t.reset().isRinging(at))
    }

    @Test fun `the alarm is set for the next running timer to finish`() {
        val a = TimerItem(id = 1, durationMs = 300_000).start(t0)
        val b = TimerItem(id = 2, durationMs = 120_000).start(t0)
        val paused = TimerItem(id = 3, durationMs = 60_000).start(t0).pause(t0 + 1)
        val finished = TimerItem(id = 4, durationMs = 10_000).start(t0)
        assertEquals(t0 + 120_000, listOf(a, b, paused, finished).nextAlarmAt(t0 + 20_000))
        assertEquals(t0 + 300_000, listOf(a, b).nextAlarmAt(t0 + 120_000))
        assertNull(listOf(paused, finished).nextAlarmAt(t0 + 20_000))
    }
}
