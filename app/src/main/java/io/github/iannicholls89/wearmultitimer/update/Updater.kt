package io.github.iannicholls89.wearmultitimer.update

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.provider.Settings
import android.widget.Toast
import androidx.core.net.toUri
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What the release workflow publishes beside each APK, as version.json. */
@Serializable
data class LatestRelease(val versionCode: Long, val versionName: String, val apkUrl: String)

/**
 * The app updating itself from its GitHub releases: it reads the latest release's version.json,
 * and when that's newer, downloads the APK and hands it to the system installer, which asks the
 * user to confirm. (As SpenDroid's watch app does, but finding the update itself - no phone.)
 */
object Updater {
    const val LATEST_URL = "https://github.com/Ian-Nicholls89/WearMultiTimer/releases/latest/download/version.json"
    private const val CHECK_EVERY_MS = 30 * 60_000L

    sealed interface Status {
        data object Idle : Status
        data object Checking : Status
        data object UpToDate : Status
        data class Available(val release: LatestRelease) : Status
        data object Downloading : Status
        data object Installing : Status
        /** [release] is the update still on offer, when it was the install that failed. */
        data class Failed(val message: String, val release: LatestRelease? = null) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status
    private var lastCheck = 0L
    private var offered: LatestRelease? = null

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): LatestRelease = json.decodeFromString(LatestRelease.serializer(), text)

    fun installedVersionCode(context: Context): Long =
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode

    fun installedVersionName(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"

    /** Looks for a newer release, unless it looked recently (or [force]). Never throws. */
    suspend fun check(context: Context, force: Boolean = false, fetch: (String) -> String = ::fetchText) {
        val busy = _status.value.let { it is Status.Checking || it is Status.Downloading || it is Status.Installing }
        if (busy || (!force && System.currentTimeMillis() - lastCheck < CHECK_EVERY_MS)) return
        lastCheck = System.currentTimeMillis()
        _status.value = Status.Checking
        _status.value = withContext(Dispatchers.IO) {
            runCatching { parse(fetch(LATEST_URL)) }.fold(
                onSuccess = { latest ->
                    if (latest.versionCode > installedVersionCode(context)) {
                        offered = latest
                        Status.Available(latest)
                    } else {
                        Status.UpToDate
                    }
                },
                onFailure = { Status.Failed("Couldn't check for updates") },
            )
        }
    }

    sealed interface InstallResult {
        data object Started : InstallResult
        data object NeedsPermission : InstallResult
        data class Failed(val message: String) : InstallResult
    }

    suspend fun install(context: Context, release: LatestRelease): InstallResult {
        // Allowed once, as "install unknown apps" for a phone app.
        if (!context.packageManager.canRequestPackageInstalls()) return InstallResult.NeedsPermission
        _status.value = Status.Downloading
        val apk = withContext(Dispatchers.IO) { runCatching { download(context, release.apkUrl) } }
            .getOrElse {
                val message = "Couldn't download the update: ${it.message ?: it.javaClass.simpleName}"
                _status.value = Status.Failed(message, release)
                return InstallResult.Failed(message)
            }
        _status.value = Status.Installing
        return withContext(Dispatchers.IO) {
            runCatching {
                val installer = context.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                    .apply { setAppPackageName(context.packageName) }
                val sessionId = installer.createSession(params)
                installer.openSession(sessionId).use { session ->
                    session.openWrite("update.apk", 0, apk.length()).use { out ->
                        apk.inputStream().use { it.copyTo(out) }
                        session.fsync(out)
                    }
                    val done = PendingIntent.getBroadcast(
                        context,
                        sessionId,
                        Intent(context, InstallResultReceiver::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                    )
                    session.commit(done.intentSender)
                }
                InstallResult.Started
            }.getOrElse {
                val message = "Couldn't start the install: ${it.message ?: it.javaClass.simpleName}"
                _status.value = Status.Failed(message, release)
                InstallResult.Failed(message)
            }
        }
    }

    /** For the tests: as when the app has just started. */
    internal fun reset() {
        lastCheck = 0
        offered = null
        _status.value = Status.Idle
    }

    /** Back to offering the update, if the install was cancelled or failed. */
    internal fun installEnded(message: String?) {
        val release = offered
        _status.value = when {
            message != null -> Status.Failed(message, release)
            release != null -> Status.Available(release)
            else -> Status.Idle
        }
    }

    /** The system's page for letting this app install updates, where the watch has one. */
    fun openPermission(activity: Context): Boolean = runCatching {
        activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${activity.packageName}".toUri()))
        true
    }.getOrDefault(false)

    private fun fetchText(url: String): String = open(url) { it.readBytes().decodeToString() }

    private fun download(context: Context, url: String): File {
        val target = File(context.cacheDir, "update.apk")
        open(url) { input -> target.outputStream().use { input.copyTo(it) } }
        return target
    }

    /** GitHub answers a release download with redirects to where the file really is. */
    private fun <T> open(url: String, read: (java.io.InputStream) -> T): T {
        var address = url
        repeat(5) {
            val connection = URL(address).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 20_000
            connection.readTimeout = 60_000
            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    address = connection.getHeaderField("Location") ?: error("redirected nowhere")
                    return@repeat
                }
                if (code != 200) error("HTTP $code")
                return connection.inputStream.use(read)
            } finally {
                connection.disconnect()
            }
        }
        error("too many redirects")
    }
}

/** Where the system says how an install went: the confirmation it needs, or the outcome. */
@SuppressLint("WearRecents") // A receiver has no task of its own: NEW_TASK is required here.
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_FAILURE_ABORTED -> Updater.installEnded(null)
            else -> {
                val message = "Update didn't install: " +
                    (intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "unknown reason")
                Updater.installEnded(message)
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }
}
