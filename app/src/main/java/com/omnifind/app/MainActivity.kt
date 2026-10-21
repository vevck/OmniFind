package com.omnifind.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.os.Build
import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.omnifind.app.release.Release
import com.omnifind.app.release.TourGate
import com.omnifind.app.ui.TourScreen
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.omnifind.app.index.FileAccess
import com.omnifind.app.index.FileKind
import com.omnifind.app.index.IndexWorker
import com.omnifind.app.search.SearchHit
import com.omnifind.app.ui.AppFrame
import com.omnifind.app.ui.EngineCheckScreen
import com.omnifind.app.ui.EngineCheckViewModel
import com.omnifind.app.ui.Files
import com.omnifind.app.ui.IndexScreen
import com.omnifind.app.ui.IndexViewModel
import com.omnifind.app.ui.PdfPageScreen
import com.omnifind.app.ui.SearchScreen
import com.omnifind.app.ui.SearchViewModel
import com.omnifind.app.ui.Tab
import com.omnifind.app.ui.theme.OmniTheme
import java.io.File

class MainActivity : ComponentActivity() {
    private val search: SearchViewModel by viewModels()
    private val index: IndexViewModel by viewModels()
    private val engineCheck: EngineCheckViewModel by viewModels()

    private val askForFiles = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        index.refresh()
        if (granted) IndexWorker.runNow(this)   // the first pass starts right away
    }

    /** Android 11+: back from the "All files access" page in Settings. */
    private val askInSettings = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        index.refresh()
        if (FileAccess.granted(this)) IndexWorker.runNow(this)
    }

    /** Android 13+: the indexing and download notifications (they show progress and a Stop button). */
    private val askForNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private fun askForFileAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            askForNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // No package-visibility lookup (Android 11+ hides it): try OmniFind's own page, else the list of all apps.
            try {
                askInSettings.launch(FileAccess.settingsIntent(this))
            } catch (_: ActivityNotFoundException) {
                askInSettings.launch(FileAccess.fallbackIntent())
            }
        } else {
            askForFiles.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge to edge on every version (Android 15+ enforces it): light bars with dark icons, as the design is light.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            OmniTheme {
                var tab by rememberSaveable { mutableStateOf(Tab.SEARCH) }
                // After a fresh install or an update to a new build: the tour of what's in this release, once.
                val release = remember { Release.load(this@MainActivity) }
                val gate = remember { TourGate(this@MainActivity) }
                var touring by rememberSaveable { mutableStateOf(gate.shouldShow(release.build)) }
                if (touring) {
                    TourScreen(release) {
                        gate.markSeen(release.build)
                        touring = false
                    }
                    return@OmniTheme
                }
                var pdf by rememberSaveable { mutableStateOf<Pair<String, Int>?>(null) }
                val open = pdf
                if (open != null) {
                    PdfPageScreen(File(open.first), open.second, onClose = { pdf = null })
                    return@OmniTheme
                }
                AppFrame(tab, { tab = it }) {
                    when (tab) {
                        Tab.SEARCH -> SearchScreen(search) { hit -> openHit(hit) { pdf = it } }
                        Tab.INDEX -> IndexScreen(index, ::askForFileAccess)
                        Tab.ENGINE -> EngineCheckScreen(engineCheck, onTour = { touring = true })
                    }
                }
            }
        }
    }

    /** A PDF opens in OmniFind at its page; anything else in the app that handles it. */
    private fun openHit(hit: SearchHit, showPdf: (Pair<String, Int>) -> Unit) {
        if (hit.kind == FileKind.PDF) showPdf(hit.path to (hit.loc ?: 1))
        else Files.open(this, File(hit.path))
    }

    override fun onResume() {
        super.onResume()
        engineCheck.refresh()   // after an adb push while the app was open
        index.refresh()
    }
}
