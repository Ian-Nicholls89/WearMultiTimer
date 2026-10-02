package io.github.iannicholls89.wearmultitimer.timer

import kotlinx.serialization.Serializable

/**
 * One countdown. Times are wall-clock milliseconds, so a running timer is just its end time:
 * nothing ticks while the app is closed, and the end time survives a restart of the watch.
 */
@Serializable
data class TimerItem(
    val id: Long,
    /** The name the user gave it; null shows the duration instead ("10 min"). */
    val name: String? = null,
    /** The duration as set. Reset goes back to this. */
    val durationMs: Long,
    /** The duration plus any +1 min since the last reset: the whole of the progress ring. */
    val totalMs: Long = durationMs,
    val state: State = State.RESET,
    /** When it reaches zero, while running. */
    val endAtMs: Long? = null,
    /** Time left, while paused or reset. */
    val remainingMs: Long = durationMs,
    val createdAtMs: Long = 0,
) {
    enum class State { RUNNING, PAUSED, RESET }

    /** Time left at [now]. Negative once a running timer has passed zero (its overtime). */
    fun remaining(now: Long): Long =
        if (state == State.RUNNING) (endAtMs ?: now) - now else remainingMs

    /** Ran out and not yet stopped. */
    fun isDone(now: Long): Boolean = state == State.RUNNING && remaining(now) <= 0

    /** Done, and still within the time it buzzes for; after that it waits silently as "Time's up". */
    fun isRinging(now: Long): Boolean = isDone(now) && -remaining(now) < RING_FOR_MS

    /** The share of the ring still to go, 1 at the start down to 0 at the end. */
    fun progress(now: Long): Float =
        if (totalMs <= 0) 0f else (remaining(now).toFloat() / totalMs).coerceIn(0f, 1f)

    fun start(now: Long): TimerItem =
        if (state == State.RUNNING) this
        else copy(state = State.RUNNING, endAtMs = now + remainingMs)

    fun pause(now: Long): TimerItem =
        if (state != State.RUNNING || isDone(now)) this
        else copy(state = State.PAUSED, endAtMs = null, remainingMs = remaining(now))

    /** As Google Clock: adds a minute, or once the time is up, starts a fresh minute. */
    fun addMinute(now: Long): TimerItem = when {
        isDone(now) -> copy(endAtMs = now + MINUTE, totalMs = MINUTE)
        state == State.RUNNING -> copy(endAtMs = endAtMs!! + MINUTE, totalMs = totalMs + MINUTE)
        state == State.PAUSED -> copy(remainingMs = remainingMs + MINUTE, totalMs = totalMs + MINUTE)
        else -> this
    }

    fun reset(): TimerItem =
        copy(state = State.RESET, endAtMs = null, remainingMs = durationMs, totalMs = durationMs)

    companion object {
        const val MINUTE = 60_000L

        /** How long a finished timer buzzes before it goes quiet, so a watch left on the side doesn't buzz on. */
        const val RING_FOR_MS = 2 * MINUTE
    }
}

/**
 * The order of the timer list: finished ones first (they need you), then running ones by the
 * soonest to finish, then paused and reset ones in the order they were made.
 */
fun List<TimerItem>.sortedForList(now: Long): List<TimerItem> = sortedWith(
    compareBy<TimerItem>(
        {
            when {
                it.isDone(now) -> 0
                it.state == TimerItem.State.RUNNING -> 1
                else -> 2
            }
        },
        { if (it.state == TimerItem.State.RUNNING) it.endAtMs else 0L },
        { it.id },
    ),
)

/**
 * How long until any running timer's display changes second, so the screen redraws exactly
 * when a digit turns over rather than on a fixed beat. Null when nothing is running.
 */
fun List<TimerItem>.millisToNextTick(now: Long): Long? = filter { it.state == TimerItem.State.RUNNING }
    .minOfOrNull {
        val toEnd = it.endAtMs!! - now
        val sub = Math.floorMod(toEnd, 1000L)
        if (sub == 0L) 1000L else sub
    }

/** When the next running timer runs out (the alarm to set), or null if none is still counting down. */
fun List<TimerItem>.nextAlarmAt(now: Long): Long? =
    filter { it.state == TimerItem.State.RUNNING && it.endAtMs!! > now }.minOfOrNull { it.endAtMs!! }
