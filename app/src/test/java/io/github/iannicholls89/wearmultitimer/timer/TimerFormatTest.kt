package io.github.iannicholls89.wearmultitimer.timer

import org.junit.Assert.assertEquals
import org.junit.Test

class TimerFormatTest {
    @Test fun `countdown rounds up, so it shows the full time for the first second`() {
        assertEquals("10:00", formatCountdown(600_000))
        assertEquals("10:00", formatCountdown(599_001))
        assertEquals("9:59", formatCountdown(599_000))
        assertEquals("0:01", formatCountdown(1))
        assertEquals("0:00", formatCountdown(0))
    }

    @Test fun `hours appear when needed`() {
        assertEquals("1:00:00", formatCountdown(3_600_000))
        assertEquals("1:02:03", formatCountdown(3_723_000))
        assertEquals("59:59", formatCountdown(3_599_000))
    }

    @Test fun `overtime counts up below zero`() {
        assertEquals("0:00", formatCountdown(-999))
        assertEquals("-0:01", formatCountdown(-1_000))
        assertEquals("-1:05", formatCountdown(-65_400))
    }

    @Test fun `durations in words`() {
        assertEquals("10 min", formatDuration(600_000))
        assertEquals("1 h 30 min", formatDuration(5_400_000))
        assertEquals("45 sec", formatDuration(45_000))
        assertEquals("2 h 5 sec", formatDuration(7_205_000))
    }

    @Test fun `an unnamed timer is called by its duration`() {
        assertEquals("10 min", TimerItem(id = 1, durationMs = 600_000).displayName())
        assertEquals("10 min", TimerItem(id = 1, name = "  ", durationMs = 600_000).displayName())
        assertEquals("Pasta", TimerItem(id = 1, name = "Pasta", durationMs = 600_000).displayName())
    }
}
