package io.github.iannicholls89.wearmultitimer.timer

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A named duration, started with one tap. Used from v0.4. */
@Serializable
data class Preset(val id: Long, val name: String, val durationMs: Long)

/** Everything the app saves, in one file written atomically. */
@Serializable
data class AppState(
    val timers: List<TimerItem> = emptyList(),
    val presets: List<Preset> = emptyList(),
    val nextId: Long = 1,
)

internal val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

internal object AppStateSerializer : Serializer<AppState> {
    override val defaultValue = AppState()

    override suspend fun readFrom(input: InputStream): AppState = try {
        json.decodeFromString(AppState.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Unreadable timers file", e)
    }

    override suspend fun writeTo(t: AppState, output: OutputStream) {
        output.write(json.encodeToString(AppState.serializer(), t).encodeToByteArray())
    }
}

private val Context.timerDataStore: DataStore<AppState> by dataStore(
    fileName = "timers.json",
    serializer = AppStateSerializer,
    // A damaged file loses the timers rather than stopping the app from opening.
    corruptionHandler = ReplaceFileCorruptionHandler { AppState() },
)

/** The saved timers and presets. One per process; the alarms (v0.3) will share it. */
class TimerStore private constructor(private val store: DataStore<AppState>) {
    val state: Flow<AppState> = store.data

    suspend fun update(transform: (AppState) -> AppState): AppState = store.updateData(transform)

    companion object {
        @Volatile private var instance: TimerStore? = null

        fun get(context: Context): TimerStore = instance ?: synchronized(this) {
            instance ?: TimerStore(context.applicationContext.timerDataStore).also { instance = it }
        }
    }
}
