package com.automatelinux.carCheck

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.automatelinux.carCheck.ui.HostActions
import com.automatelinux.carCheck.ui.feedback.FeedbackHost
import com.automatelinux.carCheck.util.ScreenTracker
import dagger.hilt.android.AndroidEntryPoint

/** Thin Android launcher: the UI is the shared App(); this file only answers the platform's questions. */
@AndroidEntryPoint
class MainActivity : ComponentActivity(), HostActions {

    private val viewModel: CarCheckViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // enableEdgeToEdge guesses bar icon colour from the device's day/night;
            // the app follows the same switch, so tell it explicitly rather than trust the guess.
            val night = isSystemInDarkTheme()
            LaunchedEffect(night) {
                enableEdgeToEdge(
                    statusBarStyle = if (night) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                    navigationBarStyle = if (night) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                )
            }
            val state by viewModel.model.state.collectAsStateWithLifecycle()
            ScreenTracker.currentScreen = if (state.report != null) "דוח רכב ${state.report?.plate?.display}" else "חיפוש"
            BackHandler(enabled = state.report != null) { viewModel.model.back() }
            FeedbackHost {
                App(model = viewModel.model, host = this)
            }
        }
    }

    override fun share(text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        open(Intent.createChooser(send, null))
    }

    override fun openUrl(url: String) = open(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    override fun dial(phone: String) = open(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone))))

    override fun copy(label: String, text: String) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, "$label הועתק", Toast.LENGTH_SHORT).show()
    }

    private fun open(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "אין אפליקציה שיכולה לפתוח את זה", Toast.LENGTH_SHORT).show()
        }
    }
}
