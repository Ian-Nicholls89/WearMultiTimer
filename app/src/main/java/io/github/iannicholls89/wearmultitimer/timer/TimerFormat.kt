package io.github.iannicholls89.wearmultitimer.timer

/** The whole seconds a countdown shows: up while counting down, so it reads 10:00 for the first second. */
fun displaySeconds(ms: Long): Long = if (ms > 0) (ms + 999) / 1000 else -((-ms) / 1000)

/** "9:41", "1:02:03", or "-0:05" for the overtime of a finished timer. */
fun formatCountdown(ms: Long): String {
    val secs = displaySeconds(ms)
    val sign = if (secs < 0) "-" else ""
    val s = kotlin.math.abs(secs)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%s%d:%02d:%02d".format(sign, h, m, sec) else "%s%d:%02d".format(sign, m, sec)
}

/** A duration in words, the name of a timer that hasn't been named: "10 min", "1 h 30 min", "45 sec". */
fun formatDuration(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return listOfNotNull(
        if (h > 0) "$h h" else null,
        if (m > 0) "$m min" else null,
        if (sec > 0) "$sec sec" else null,
    ).joinToString(" ").ifEmpty { "0 sec" }
}

fun TimerItem.displayName(): String = name?.takeIf { it.isNotBlank() } ?: formatDuration(durationMs)

/** For the screen reader: "9 minutes 41 seconds", "1 hour 2 minutes", "0 seconds". */
fun spokenDuration(ms: Long): String {
    val s = kotlin.math.abs(displaySeconds(ms))
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    fun unit(n: Long, one: String) = "$n $one" + if (n == 1L) "" else "s"
    return listOfNotNull(
        if (h > 0) unit(h, "hour") else null,
        if (m > 0) unit(m, "minute") else null,
        if (sec > 0) unit(sec, "second") else null,
    ).joinToString(" ").ifEmpty { "0 seconds" }
}

/** What the screen reader says for a timer: "Pasta. Timer running, 9 minutes 41 seconds left." */
fun TimerItem.spokenStatus(now: Long): String {
    val left = remaining(now)
    val status = when {
        isDone(now) -> "Timer finished, ${spokenDuration(left)} over"
        state == TimerItem.State.RUNNING -> "Timer running, ${spokenDuration(left)} left"
        state == TimerItem.State.PAUSED -> "Timer paused, ${spokenDuration(left)} left"
        else -> "Timer not started, ${spokenDuration(left)}"
    }
    return "${displayName()}. $status."
}
