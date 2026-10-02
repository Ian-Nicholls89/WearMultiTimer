package io.github.iannicholls89.wearmultitimer.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
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
 * Buzzes (and plays the alarm sound unless the watch is on vibrate or silent) while any timer is
 * ringing, and stops by itself once none is: stopped, given +1 min, or quiet after two minutes.
 * The only time the app runs in the background - nothing runs just to count down.
 */
class RingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watching: Job? = null
    private var alerting = false
    private var ringtone: Ringtone? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!alerting) {
            goForeground()
            startAlert()
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

        // Sound only when the watch isn't on vibrate or silent.
        if (getSystemService(AudioManager::class.java).ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ringtone = uri?.let { RingtoneManager.getRingtone(this, it) }?.apply {
                audioAttributes = alarmAudio
                isLooping = true
                play()
            }
        }
    }

    private fun stopAlert() {
        if (!alerting) return
        alerting = false
        vibrator().cancel()
        ringtone?.stop()
        ringtone = null
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
