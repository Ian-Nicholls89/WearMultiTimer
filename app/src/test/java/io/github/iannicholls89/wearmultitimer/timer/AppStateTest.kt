package io.github.iannicholls89.wearmultitimer.timer

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What's saved is what comes back - the "restart of the watch" case: only the clock has moved on. */
class AppStateTest {
    private fun roundTrip(s: AppState): AppState = runBlocking {
        val out = ByteArrayOutputStream()
        AppStateSerializer.writeTo(s, out)
        AppStateSerializer.readFrom(ByteArrayInputStream(out.toByteArray()))
    }

    @Test fun `timers survive being saved and read back`() {
        val t0 = 1_700_000_000_000L
        val state = AppState(
            timers = listOf(
                TimerItem(id = 1, name = "Pasta", durationMs = 600_000, createdAtMs = t0).start(t0),
                TimerItem(id = 2, durationMs = 300_000).start(t0).pause(t0 + 60_000),
                TimerItem(id = 3, durationMs = 45_000),
            ),
            presets = listOf(Preset(1, "Eggs", 420_000)),
            nextId = 4,
        )
        val back = roundTrip(state)
        assertEquals(state, back)
        // After a restart five minutes on, the running one has carried on and the paused one hasn't.
        val later = t0 + 300_000
        assertEquals(300_000, back.timers[0].remaining(later))
        assertEquals(240_000, back.timers[1].remaining(later))
    }

    @Test fun `fields added in later versions don't break reading an older file`() {
        val old = """{"timers":[{"id":7,"durationMs":60000}],"nextId":8,"somethingNew":true}"""
        val s = runBlocking { AppStateSerializer.readFrom(ByteArrayInputStream(old.toByteArray())) }
        assertEquals(7L, s.timers.single().id)
        assertTrue(s.presets.isEmpty())
    }
}
