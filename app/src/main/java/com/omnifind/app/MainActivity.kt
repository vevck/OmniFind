package com.omnifind.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
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
        if (FileAccess.needsSettings) askInSettings.launch(FileAccess.settingsIntent(this))
        else askForFiles.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OmniTheme {
                var tab by rememberSaveable { mutableStateOf(Tab.SEARCH) }
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
                        Tab.ENGINE -> EngineCheckScreen(engineCheck)
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
