package io.github.iannicholls89.wearmultitimer

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import io.github.iannicholls89.wearmultitimer.timer.TimerStore
import io.github.iannicholls89.wearmultitimer.ui.CustomDurationScreen
import io.github.iannicholls89.wearmultitimer.ui.NewTimerScreen
import io.github.iannicholls89.wearmultitimer.ui.TimerListScreen
import io.github.iannicholls89.wearmultitimer.ui.TimerScreen
import io.github.iannicholls89.wearmultitimer.ui.TimerTheme
import io.github.iannicholls89.wearmultitimer.ui.rememberNow

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
fun WearMultiTimerApp(clock: () -> Long = System::currentTimeMillis) {
    val context = LocalContext.current
    val vm: TimerViewModel = viewModel { TimerViewModel(TimerStore.get(context), clock) }
    val nav = rememberSwipeDismissableNavController()

    // A new timer opens on its own screen, as in Google Clock; swipe back for the list.
    val openNew: (Long) -> Unit = { id ->
        nav.navigate("timer/$id") { popUpTo("list") }
    }

    TimerTheme {
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
                    )
                }
                composable("new") {
                    NewTimerScreen(
                        onPick = { ms -> vm.create(ms, onCreated = openNew) },
                        onCustom = { nav.navigate("custom") },
                    )
                }
                composable("custom") {
                    CustomDurationScreen { ms ->
                        if (ms <= 0) Toast.makeText(context, "Set a time first", Toast.LENGTH_SHORT).show()
                        else vm.create(ms, onCreated = openNew)
                    }
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
                        TimerScreen(
                            timer = timer,
                            now = now,
                            onStartPause = { vm.startOrPause(timer.id) },
                            onAddMinute = { vm.addMinute(timer.id) },
                            onReset = { vm.reset(timer.id) },
                            onDelete = { vm.delete(timer.id) },
                        )
                    }
                }
            }
        }
    }
}
