package io.github.iannicholls89.wearmultitimer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.iannicholls89.wearmultitimer.timer.AppState
import io.github.iannicholls89.wearmultitimer.timer.TimerItem
import io.github.iannicholls89.wearmultitimer.timer.TimerStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TimerViewModel(
    private val store: TimerStore,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    /** Null until the saved timers have been read, so the list doesn't flash "No timers yet". */
    val timers: StateFlow<List<TimerItem>?> = store.state
        .map { it.timers }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Makes a timer and starts it; [onCreated] gets its id, to open its screen. */
    fun create(durationMs: Long, name: String? = null, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val now = clock()
            var id = 0L
            store.update { s ->
                id = s.nextId
                val timer = TimerItem(id = id, name = name, durationMs = durationMs, createdAtMs = now).start(now)
                s.copy(timers = s.timers + timer, nextId = id + 1)
            }
            onCreated(id)
        }
    }

    fun startOrPause(id: Long) = edit(id) { t, now ->
        if (t.state == TimerItem.State.RUNNING) t.pause(now) else t.start(now)
    }

    fun addMinute(id: Long) = edit(id) { t, now -> t.addMinute(now) }

    fun reset(id: Long) = edit(id) { t, _ -> t.reset() }

    fun delete(id: Long) {
        viewModelScope.launch { store.update { s -> s.copy(timers = s.timers.filterNot { it.id == id }) } }
    }

    private fun edit(id: Long, change: (TimerItem, Long) -> TimerItem) {
        viewModelScope.launch {
            val now = clock()
            store.update { s: AppState -> s.copy(timers = s.timers.map { if (it.id == id) change(it, now) else it }) }
        }
    }
}
