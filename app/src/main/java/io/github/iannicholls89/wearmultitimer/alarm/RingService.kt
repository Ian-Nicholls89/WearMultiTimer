package io.github.iannicholls89.wearmultitimer.alarm

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.iannicholls89.wearmultitimer.R
import io.github.iannicholls89.wearmultitimer.RingActivity
import io.github.iannicholls89.wearmultitimer.timer.TimerItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Buzzes and chimes while any timer is ringing, and stops by itself once none is: stopped, given
 * +1 min, or quiet after two minutes. The only time the app runs in the background - nothing runs
 * just to count down.
 */
class RingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watching: Job? = null
    private var alerting = false
    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    /** For the tests. */
    internal val isChiming: Boolean get() = player?.isPlaying == true

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!alerting) {
            goForeground()
            startAlert()
            showAlertScreen()
        }
        // Started again when another timer runs out while ringing: look again at what's ringing.
        watching?.cancel()
        watching = scope.launch { watch() }
        return START_NOT_STICKY
    }

    private fun goForeground() {
        val notification = Notifications.ringing(this, emptyList())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                // The type for alarm apps' ringing.
                startForeground(Notifications.RINGING_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
            } catch (e: SecurityException) {
                Log.w(TAG, "systemExempted refused, using specialUse", e)
                startForeground(Notifications.RINGING_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            }
        } else {
            startForeground(Notifications.RINGING_ID, notification)
        }
    }

    private suspend fun watch() {
        val controller = TimerController.get(this)
        controller.store.state.collectLatest { state ->
            while (true) {
                val now = System.currentTimeMillis()
                val ringing = state.timers.filter { it.isRinging(now) }
                if (ringing.isEmpty()) {
                    finish(state.timers, now)
                    return@collectLatest
                }
                if (Notifications.canPost(this@RingService)) {
                    try {
                        NotificationManagerCompat.from(this@RingService)
                            .notify(Notifications.RINGING_ID, Notifications.ringing(this@RingService, ringing))
                    } catch (_: SecurityException) {
                    }
                }
                // Until the first of them goes quiet.
                delay((ringing.minOf { it.endAtMs!! } + TimerItem.RING_FOR_MS - now).coerceAtLeast(50))
            }
        }
    }

    private fun finish(timers: List<TimerItem>, now: Long) {
        stopAlert()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        Notifications.showFinished(this, timers, now)
        stopSelf()
    }

    private fun startAlert() {
        alerting = true
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WearMultiTimer:ring")
            .apply { acquire(TimerItem.RING_FOR_MS + 30_000) }

        val pattern = VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator().vibrate(pattern, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator().vibrate(pattern, alarmAudio)
        }

        // As Google Clock: the chime plays at the alarm volume whether the watch is on sound or
        // vibrate; only silent mode keeps it quiet. Our own sound - a watch may have no alarm tones.
        if (getSystemService(AudioManager::class.java).ringerMode != AudioManager.RINGER_MODE_SILENT) {
            player = try {
                MediaPlayer().apply {
                    setAudioAttributes(alarmAudio)
                    resources.openRawResourceFd(R.raw.timer_chime).use { setDataSource(it) }
                    isLooping = true
                    prepare()
                    start()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't play the chime", e)
                null
            }
        }
    }

    /**
     * Fills the screen with the alert, as Google Clock does. The notification asks for that too,
     * but Wear OS may only show it as a notification; opening it from here works when the app may
     * display over other apps (granted by ADB - see the README), and is quietly refused otherwise.
     */
    @SuppressLint("WearRecents") // A service has no task of its own: NEW_TASK is required here.
    private fun showAlertScreen() {
        try {
            startActivity(Intent(this, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't open the alert screen", e)
        }
    }

    private fun stopAlert() {
        if (!alerting) return
        alerting = false
        vibrator().cancel()
        player?.run {
            try {
                stop()
            } catch (_: IllegalStateException) {
            }
            release()
        }
        player = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun vibrator(): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }

    override fun onDestroy() {
        stopAlert()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "RingService"

        private val alarmAudio: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, RingService::class.java))
            } catch (e: IllegalStateException) {
                // Not allowed from here (it should be, from the alarm and the app): the notification still shows.
                Log.w(TAG, "Couldn't start ringing", e)
            }
        }
    }
}
