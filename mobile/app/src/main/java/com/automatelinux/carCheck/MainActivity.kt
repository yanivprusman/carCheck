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
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.automatelinux.carCheck.ui.HostActions
import com.automatelinux.carCheck.ui.ImageSource
import com.automatelinux.carCheck.ui.feedback.FeedbackHost
import com.automatelinux.carCheck.util.ScreenTracker
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

/** Thin Android launcher: the UI is the shared App(); this file only answers the platform's questions. */
@AndroidEntryPoint
class MainActivity : ComponentActivity(), HostActions {

    private val viewModel: CarCheckViewModel by viewModels()

    private var cameraFile: File? = null

    private val gallery = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) readPlate(uri)
    }

    private val camera = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = cameraFile
        if (ok && f != null && f.length() > 0) readPlate(Uri.fromFile(f))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A picture shared into the app is read once, not again on every rotation.
        if (savedInstanceState == null) sharedImage(intent)?.let { readPlate(it) }
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedImage(intent)?.let { readPlate(it) }
    }

    /** The image another app shared to us, if this launch is a share. */
    private fun sharedImage(intent: Intent?): Uri? {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("image/") != true) return null
        return IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
    }

    private fun readPlate(uri: Uri) {
        viewModel.model.readPlates { PlateScanner.read(applicationContext, uri) }
    }

    override fun scanPlate(source: ImageSource) {
        when (source) {
            ImageSource.Gallery -> gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            ImageSource.Camera -> {
                val f = File(File(cacheDir, "camera").apply { mkdirs() }, "plate.jpg")
                cameraFile = f
                try {
                    camera.launch(FileProvider.getUriForFile(this, "$packageName.files", f))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(this, "אין אפליקציית מצלמה", Toast.LENGTH_SHORT).show()
                }
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
