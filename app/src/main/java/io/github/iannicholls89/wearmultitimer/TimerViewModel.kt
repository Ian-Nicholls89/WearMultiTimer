package io.github.iannicholls89.wearmultitimer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.iannicholls89.wearmultitimer.alarm.TimerController
import io.github.iannicholls89.wearmultitimer.timer.Preset
import io.github.iannicholls89.wearmultitimer.timer.TimerItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TimerViewModel(
    private val controller: TimerController,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    /** Null until the saved timers have been read, so the list doesn't flash "No timers yet". */
    val timers: StateFlow<List<TimerItem>?> = controller.store.state
        .map { it.timers }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val presets: StateFlow<List<Preset>?> = controller.store.state
        .map { it.presets }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Makes a timer and starts it, and with [saveAsPreset] keeps its name and duration as a preset
     * too (once - the same name and duration aren't saved twice). [onCreated] gets the timer's id.
     */
    fun create(durationMs: Long, name: String? = null, saveAsPreset: Boolean = false, onCreated: (Long) -> Unit = {}) {
        val cleanName = name?.trim()?.takeIf { it.isNotEmpty() }
        viewModelScope.launch {
            val now = clock()
            var id = 0L
            controller.update(now) { s ->
                id = s.nextId
                val timer = TimerItem(id = id, name = cleanName, durationMs = durationMs, createdAtMs = now).start(now)
                val keep = saveAsPreset && cleanName != null &&
                    s.presets.none { it.name == cleanName && it.durationMs == durationMs }
                s.copy(
                    timers = s.timers + timer,
                    presets = if (keep) s.presets + Preset(id = id, name = cleanName!!, durationMs = durationMs) else s.presets,
                    nextId = id + 1,
                )
            }
            onCreated(id)
        }
    }

    fun startPreset(presetId: Long, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val now = clock()
            var id = 0L
            controller.update(now) { s ->
                val preset = s.presets.firstOrNull { it.id == presetId } ?: return@update s
                id = s.nextId
                val timer = TimerItem(id = id, name = preset.name, durationMs = preset.durationMs, createdAtMs = now).start(now)
                s.copy(timers = s.timers + timer, nextId = id + 1)
            }
            if (id != 0L) onCreated(id)
        }
    }

    fun deletePreset(presetId: Long) {
        val now = clock()
        viewModelScope.launch { controller.update(now) { s -> s.copy(presets = s.presets.filterNot { it.id == presetId }) } }
    }

    /** A blank name goes back to showing the duration. */
    fun rename(id: Long, name: String) = edit(id) { t, _ -> t.copy(name = name.trim().takeIf { it.isNotEmpty() }) }

    fun startOrPause(id: Long) = edit(id) { t, now ->
        if (t.state == TimerItem.State.RUNNING) t.pause(now) else t.start(now)
    }

    fun addMinute(id: Long) = edit(id) { t, now -> t.addMinute(now) }

    fun reset(id: Long) = edit(id) { t, _ -> t.reset() }

    fun delete(id: Long) {
        val now = clock()
        viewModelScope.launch { controller.update(now) { s -> s.copy(timers = s.timers.filterNot { it.id == id }) } }
    }

    private fun edit(id: Long, change: (TimerItem, Long) -> TimerItem) {
        viewModelScope.launch {
            val now = clock()
            controller.update(now) { s -> s.copy(timers = s.timers.map { if (it.id == id) change(it, now) else it }) }
        }
    }
}
