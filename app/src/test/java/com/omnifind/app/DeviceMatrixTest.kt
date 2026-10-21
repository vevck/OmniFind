package com.omnifind.app

import android.app.Application
import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.captureRoboImage
import com.omnifind.app.index.FileKind
import com.omnifind.app.index.IndexStore
import com.omnifind.app.ui.AppFrame
import com.omnifind.app.ui.EngineActions
import com.omnifind.app.ui.EngineContent
import com.omnifind.app.ui.EngineUi
import com.omnifind.app.ui.FolderUi
import com.omnifind.app.ui.IndexActions
import com.omnifind.app.ui.IndexContent
import com.omnifind.app.ui.IndexType
import com.omnifind.app.ui.IndexUi
import com.omnifind.app.ui.SearchContent
import com.omnifind.app.ui.SearchUi
import com.omnifind.app.ui.Tab
import com.omnifind.app.ui.VolumeUi
import com.omnifind.app.ui.theme.OmniTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The three screens on the screens Android sorts into each density bucket (ldpi to xxxhdpi), on a Pixel 8 Pro, a
 * tablet and a landscape phone, and at large font sizes: app/build/screens/devices/<device>-<screen>.png. Read with
 * tools/ux/contact_sheet.py. A layout that clips, overlaps or runs off the edge shows up here.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class DeviceMatrixTest {
    /** name to (qualifiers, font scale). Sizes are each bucket's classic screen: px ÷ (dpi / 160) = dp. */
    private val devices = listOf(
        "ldpi-240x320" to ("w320dp-h427dp-ldpi" to 1f),           // 240 × 320 px at 120 dpi
        "mdpi-320x480" to ("w320dp-h480dp-mdpi" to 1f),           // 320 × 480 px at 160 dpi (baseline)
        "hdpi-480x800" to ("w320dp-h533dp-hdpi" to 1f),           // 480 × 800 px at 240 dpi
        "xhdpi-720x1280" to ("w360dp-h640dp-xhdpi" to 1f),        // 720 × 1280 px at 320 dpi
        "xxhdpi-MiA1-1080x1920" to ("w360dp-h640dp-xxhdpi" to 1f),  // the Mi A1: 1080 × 1920 px at 480 dpi
        "xxhdpi-Pixel8Pro-1344x2992" to ("w448dp-h997dp-xxhdpi" to 1f),  // Pixel 8 Pro: 1344 × 2992 px at 480 dpi
        "xxxhdpi-1440x2560" to ("w360dp-h640dp-xxxhdpi" to 1f),   // 1440 × 2560 px at 640 dpi
        "tablet-sw800-xhdpi" to ("w800dp-h1280dp-xhdpi" to 1f),
        "landscape-Pixel8Pro" to ("w997dp-h448dp-land-xxhdpi" to 1f),
        "font130-MiA1" to ("w360dp-h640dp-xxhdpi" to 1.3f),
        "font200-MiA1" to ("w360dp-h640dp-xxhdpi" to 2f),
    )

    private val stats = IndexStore.Stats(
        files = 150, done = 150, failed = 0, skipped = 0, pieces = 93, embedded = 80, waiting = 13,
        picturesWaiting = mapOf(FileKind.IMAGE to 12), picturesEmbedded = 45,
        byKind = mapOf(FileKind.DOC to 93, FileKind.IMAGE to 57),
    )
    private val volumes = listOf(
        VolumeUi("Phone storage", false, listOf(
            FolderUi("", "/storage/emulated/0", true, false),
            FolderUi("DCIM", "/storage/emulated/0/DCIM", false, true),
            FolderUi("Documents", "/storage/emulated/0/Documents", false, true),
            FolderUi("Download", "/storage/emulated/0/Download", false, true),
        )),
        VolumeUi("SD card", true, listOf(FolderUi("", "/storage/1A2B-3C4D", true, true))),
    )

    @Test
    fun allDevices() {
        for ((name, config) in devices) {
            val (qualifiers, fontScale) = config
            RuntimeEnvironment.setQualifiers(qualifiers)
            RuntimeEnvironment.setFontScale(fontScale)
            shot("$name-search", Tab.SEARCH) { SearchContent(SearchUi(), {}, {}, {}) }
            shot("$name-index", Tab.INDEX) {
                IndexContent(IndexUi(stats = stats, lastRun = "12 new or changed · 138 skipped · 9 embedded",
                    lastRunAt = 1_792_340_000_000, chosen = setOf(IndexType.DOCUMENTS, IndexType.PICTURES),
                    volumes = volumes), NoIndexActions)
            }
            shot("$name-engine", Tab.ENGINE) { EngineContent(EngineUi(), NoEngineActions) }
        }
    }

    private fun shot(name: String, tab: Tab, content: @Composable () -> Unit) =
        captureRoboImage("build/screens/devices/$name.png") { OmniTheme { AppFrame(tab, {}) { content() } } }

    private object NoIndexActions : IndexActions {
        override fun askPermission() {}
        override fun indexNow() {}
        override fun embedNow() {}
        override fun stop() {}
        override fun toggle(type: IndexType) {}
        override fun toggleFolder(path: String) {}
        override fun choosePhotoBudget(budget: Int) {}
        override fun chooseScreenshotBudget(budget: Int) {}
        override fun chooseThreads(count: Int) {}
        override fun changeEmbedWhileCharging(enabled: Boolean) {}
        override fun confirmIndexing(dontAskAgain: Boolean) {}
        override fun cancelIndexing() {}
    }

    private object NoEngineActions : EngineActions {
        override fun refresh() {}
        override fun verify() {}
        override fun search(query: String) {}
        override fun chooseThreads(count: Int) {}
        override fun benchmark() {}
    }
}
