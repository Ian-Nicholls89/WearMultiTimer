package io.github.iannicholls89.wearmultitimer.ui

import android.app.RemoteInput
import android.view.inputmethod.EditorInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.wear.input.RemoteInputIntentHelper
import androidx.wear.input.wearableExtender

private const val KEY = "text"

/** The watch's own text entry - keyboard, voice or handwriting, whatever it offers. Returns a launcher. */
@Composable
fun rememberTextInput(label: String, onText: (String) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.let { RemoteInput.getResultsFromIntent(it) }?.getCharSequence(KEY)?.toString()?.let(onText)
    }
    return {
        val input = RemoteInput.Builder(KEY)
            .setLabel(label)
            .wearableExtender {
                setEmojisAllowed(false)
                setInputActionType(EditorInfo.IME_ACTION_DONE)
            }
            .build()
        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(input))
        launcher.launch(intent)
    }
}
