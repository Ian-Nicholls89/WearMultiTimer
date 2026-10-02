package io.github.iannicholls89.wearmultitimer

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import io.github.iannicholls89.wearmultitimer.alarm.Notifications
import io.github.iannicholls89.wearmultitimer.alarm.TimerController
import io.github.iannicholls89.wearmultitimer.ui.CustomDurationScreen
import io.github.iannicholls89.wearmultitimer.ui.NameTimerScreen
import io.github.iannicholls89.wearmultitimer.ui.NewTimerScreen
import io.github.iannicholls89.wearmultitimer.ui.Notice
import io.github.iannicholls89.wearmultitimer.ui.TimerListScreen
import io.github.iannicholls89.wearmultitimer.ui.TimerScreen
import io.github.iannicholls89.wearmultitimer.ui.TimerTheme
import io.github.iannicholls89.wearmultitimer.ui.UpdateUi
import io.github.iannicholls89.wearmultitimer.update.Updater
import io.github.iannicholls89.wearmultitimer.ui.rememberNow
import io.github.iannicholls89.wearmultitimer.ui.rememberTextInput

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WearMultiTimerApp() }
    }
}

/**
 * Each screen reads the timers and the time itself: the nav host keeps the first version of each
 * destination's content, so values read out here and passed in would never change on screen.
 */
@Composable
fun WearMultiTimerApp(clock: () -> Long = System::currentTimeMillis, checkForUpdates: Boolean = true) {
    val context = LocalContext.current
    val vm: TimerViewModel = viewModel { TimerViewModel(TimerController.get(context), clock) }
    val nav = rememberSwipeDismissableNavController()

    // A new timer opens on its own screen, as in Google Clock; swipe back for the list.
    val openNew: (Long) -> Unit = { id ->
        nav.navigate("timer/$id") { popUpTo("list") }
    }

    TimerTheme {
        OpenRingScreenWhenDone(vm, clock)
        AppScaffold {
            SwipeDismissableNavHost(navController = nav, startDestination = "list") {
                composable("list") {
                    val timers by vm.timers.collectAsStateWithLifecycle()
                    TimerListScreen(
                        timers = timers,
                        now = rememberNow(timers.orEmpty(), clock),
                        onOpen = { nav.navigate("timer/$it") },
                        onDelete = vm::delete,
                        onNew = { nav.navigate("new") },
                        notices = rememberAlertNotices(),
                        update = rememberUpdateUi(checkForUpdates),
                    )
                }
                composable("new") {
                    val presets by vm.presets.collectAsStateWithLifecycle()
                    NewTimerScreen(
                        onPick = { ms -> nav.navigate("name/$ms") },
                        onCustom = { nav.navigate("custom") },
                        presets = presets.orEmpty(),
                        onPreset = { id -> vm.startPreset(id, onCreated = openNew) },
                        onDeletePreset = vm::deletePreset,
                    )
                }
                composable("custom") {
                    CustomDurationScreen { ms ->
                        if (ms <= 0) Toast.makeText(context, "Set a time first", Toast.LENGTH_SHORT).show()
                        else nav.navigate("name/$ms")
                    }
                }
                composable("name/{ms}") { entry ->
                    val ms = entry.arguments?.getString("ms")?.toLongOrNull() ?: return@composable
                    var name by rememberSaveable { mutableStateOf<String?>(null) }
                    var save by rememberSaveable { mutableStateOf(false) }
                    val editName = rememberTextInput("Name this timer") { name = it.trim().ifEmpty { null } }
                    NameTimerScreen(
                        durationMs = ms,
                        name = name,
                        saveAsPreset = save,
                        onEditName = editName,
                        onSaveAsPresetChange = { save = it },
                        onStart = { vm.create(ms, name, saveAsPreset = save, onCreated = openNew) },
                    )
                }
                composable("timer/{id}") { entry ->
                    val id = entry.arguments?.getString("id")?.toLongOrNull()
                    val timers by vm.timers.collectAsStateWithLifecycle()
                    val timer = timers?.firstOrNull { it.id == id }
                    val now = rememberNow(listOfNotNull(timer), clock)
                    if (timer == null) {
                        // Deleted (or not read yet): back to the list once the timers are in.
                        if (timers != null) LaunchedEffect(Unit) { nav.popBackStack("list", inclusive = false) }
                    } else {
                        val rename = rememberTextInput("Rename this timer") { vm.rename(timer.id, it) }
                        TimerScreen(
                            timer = timer,
                            now = now,
                            onStartPause = { vm.startOrPause(timer.id) },
                            onAddMinute = { vm.addMinute(timer.id) },
                            onReset = { vm.reset(timer.id) },
                            onDelete = { vm.delete(timer.id) },
                            onRename = rename,
                        )
                    }
                }
            }
        }
    }
}

/**
 * With the app open, the system shows the alert as a pop-up rather than full screen; open the
 * full-screen one ourselves whenever another timer starts ringing.
 */
@Composable
private fun OpenRingScreenWhenDone(vm: TimerViewModel, clock: () -> Long) {
    val context = LocalContext.current
    val timers by vm.timers.collectAsStateWithLifecycle()
    val now = rememberNow(timers.orEmpty(), clock)
    val ringing = timers.orEmpty().filter { it.isRinging(now) }.map { it.id }.toSet()
    LaunchedEffect(ringing) {
        if (ringing.isNotEmpty()) {
            context.startActivity(Intent(context, RingActivity::class.java))
        }
    }
}

/** Asks for notifications once, and lists whatever would stop a finished timer from alerting. */
@Composable
private fun rememberAlertNotices(): List<Notice> {
    val context = LocalContext.current
    var checks by remember { mutableIntStateOf(0) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { checks++ }
    var asked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !asked && !Notifications.canPost(context)) {
            asked = true
            ask.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    // Looked at again on coming back from Settings.
    LifecycleResumeEffect(Unit) {
        checks++
        onPauseOrDispose {}
    }
    return remember(checks) { alertNotices(context) }
}

private fun alertNotices(context: Context): List<Notice> = buildList {
    if (!Notifications.canPost(context)) {
        add(Notice("Notifications off: timers can't alert you. Tap to fix.") {
            openSettings(
                context,
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        })
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        !context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    ) {
        add(Notice("Full-screen alerts off. Tap to allow.") {
            openSettings(
                context,
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:${context.packageName}".toUri()),
            )
        })
    }
    if (!Settings.canDrawOverlays(context)) {
        add(Notice("Alerts can't fill the screen yet: allow display over apps (see README).") {
            openSettings(context, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri()))
        })
    }
}

/** The watch may not have that exact settings screen; the app's own page has the same switches. */
private fun openSettings(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
    }
}

/** Looks for a newer release whenever the list comes on screen (at most every half hour), and offers it. */
@Composable
private fun rememberUpdateUi(checkForUpdates: Boolean): UpdateUi {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status by Updater.status.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    if (checkForUpdates) {
        LaunchedEffect(lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { Updater.check(context) }
        }
    }
    val release = when (val s = status) {
        is Updater.Status.Available -> s.release
        is Updater.Status.Failed -> s.release
        else -> null
    }
    val busy = status is Updater.Status.Checking || status is Updater.Status.Downloading ||
        status is Updater.Status.Installing
    return UpdateUi(
        installed = remember { Updater.installedVersionName(context) },
        line = when (val s = status) {
            Updater.Status.Idle -> "Tap to check for updates"
            Updater.Status.Checking -> "Checking for updates…"
            Updater.Status.UpToDate -> "Up to date"
            is Updater.Status.Available -> "${s.release.versionName} is out"
            Updater.Status.Downloading -> "Downloading the update…"
            Updater.Status.Installing -> "Installing…"
            is Updater.Status.Failed -> s.message
        },
        offer = when {
            status is Updater.Status.Downloading -> "Downloading…"
            status is Updater.Status.Installing -> "Installing…"
            release != null -> "Update to ${release.versionName}"
            else -> null
        },
        busy = busy,
        onInstall = {
            val r = release ?: return@UpdateUi
            scope.launch {
                when (Updater.install(context, r)) {
                    Updater.InstallResult.NeedsPermission -> {
                        Toast.makeText(context, "Allow this app to install updates, then tap Update again", Toast.LENGTH_LONG).show()
                        if (!Updater.openPermission(context)) {
                            Toast.makeText(context, "No setting for it on this watch: see the README for the ADB command", Toast.LENGTH_LONG).show()
                        }
                    }
                    is Updater.InstallResult.Failed, Updater.InstallResult.Started -> Unit
                }
            }
        },
        onCheck = { scope.launch { Updater.check(context, force = true) } },
    )
}
