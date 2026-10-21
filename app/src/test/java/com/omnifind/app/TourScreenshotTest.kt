package com.omnifind.app

import android.app.Application
import com.omnifind.app.help.Guide
import com.omnifind.app.help.ReleaseNotes
import com.omnifind.app.ui.help.HelpPages
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.omnifind.app.index.FileKind
import com.omnifind.app.index.IndexStore
import com.omnifind.app.index.SegmentKind
import com.omnifind.app.release.Release
import com.omnifind.app.search.SearchHit
import com.omnifind.app.ui.AppFrame
import com.omnifind.app.ui.EngineActions
import com.omnifind.app.ui.EngineContent
import com.omnifind.app.ui.EngineUi
import com.omnifind.app.ui.FolderUi
import com.omnifind.app.ui.FoldersSection
import com.omnifind.app.ui.IndexActions
import com.omnifind.app.ui.IndexContent
import com.omnifind.app.ui.IndexType
import com.omnifind.app.ui.IndexUi
import com.omnifind.app.ui.SearchContent
import com.omnifind.app.ui.SearchViewModel
import com.omnifind.app.ui.SearchUi
import com.omnifind.app.ui.Tab
import com.omnifind.app.ui.TourContent
import com.omnifind.app.ui.VolumeUi
import com.omnifind.app.ui.theme.OmniSpace
import com.omnifind.app.ui.theme.OmniTheme
import com.omnifind.app.ui.theme.ScreenTitle
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Build validation's screenshots for the release tour (release.json → tour[].screenshot): each step's screen with
 * representative data, on a 412 × 892 dp phone at 2×, into app/build/screens/tour/. tools/release/make_docs.py turns
 * them into the app's tour images (assets/release/tour, as WebP) and the documents' figures.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xhdpi", application = Application::class)
class TourScreenshotTest {
    private val out = "build/screens/tour"
    private fun shot(name: String, tab: Tab, content: @Composable () -> Unit) =
        captureRoboImage("$out/$name.png") { OmniTheme { AppFrame(tab, {}) { content() } } }

    @Test
    fun capture() {
        shot("search-results", Tab.SEARCH) {
            SearchContent(SearchUi(query = "the clause about pets in the lease", hits = hits, strong = 2), {}, {}, {})
        }
        // Fallback for the Pictures step when there's no device capture (app/build/screens/emulator/pictures.png).
        shot("pictures", Tab.SEARCH) {
            SearchContent(SearchUi(query = "mountains by a lake", filter = SearchViewModel.Filter.PICTURES,
                hits = listOf(hit("/storage/emulated/0/Pictures/mountain_lake.jpg", FileKind.IMAGE, SegmentKind.IMAGE, 0, "")),
                strong = 1), {}, {}, {})
        }
        shot("index-types", Tab.INDEX) {
            IndexContent(IndexUi(stats = stats, lastRun = "12 new or changed · 138 skipped · 9 embedded",
                lastRunAt = 1_792_340_000_000, chosen = setOf(IndexType.DOCUMENTS, IndexType.PICTURES)), NoIndex)
        }
        shot("index-folders", Tab.INDEX) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = OmniSpace.Margin)) {
                Spacer(Modifier.height(19.dp))
                ScreenTitle("Index")
                Spacer(Modifier.height(20.dp))
                FoldersSection(IndexUi(volumes = volumes), NoIndex)
            }
        }
        shot("engine-download", Tab.ENGINE) {
            EngineContent(EngineUi(textModel = false, mediaModel = false, folder = "/storage/emulated/0/Android/data/com.omnifind.app/files/models",
                download = "Downloading: 312 of 865 MB", downloadFraction = 312f / 865f), NoEngine)
        }
        // Help, from the menu: the User Guide.
        captureRoboImage("$out/help-guide.png") {
            OmniTheme {
                HelpPages(listOf("help"), {}, Guide.parse(File("src/main/assets/help/guide.json").readText()),
                    ReleaseNotes.parse(File("src/main/assets/help/release_notes.json").readText()), { null })
            }
        }
        // The tour itself, showing its first step with the screenshot just captured.
        val release = Release.parse(File("src/main/assets/release/release.json").readText())
        val first = BitmapFactory.decodeFile("$out/search-results.png").asImageBitmap()
        captureRoboImage("$out/tour.png") { OmniTheme { TourContent(release, { first }, {}) } }
    }

    private val hits = listOf(
        hit("/storage/emulated/0/Documents/Lease 2026.pdf", FileKind.PDF, SegmentKind.PAGE, 3,
            "…the tenant may keep one «cat» or one small dog with the landlord's written consent…"),
        hit("/storage/emulated/0/Download/House rules.docx", FileKind.DOC, SegmentKind.CHUNK, 0,
            "Pets: residents must register any animal with the building office…"),
        hit("/storage/1A2B-3C4D/Notes/moving checklist.md", FileKind.DOC, SegmentKind.CHUNK, 0,
            "Ask the landlord about keeping the cat; deposit for pets is one month…"),
    )

    private fun hit(path: String, kind: FileKind, segment: SegmentKind, loc: Int, excerpt: String) =
        SearchHit(path, kind, 4.2, 4.0, segment, loc, excerpt, 1_792_000_000.0, 120_000)

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
            FolderUi("Scans", "/storage/emulated/0/Scans", false, false, isNew = true),
        ), refreshNote = "1 new folder: Scans"),
        VolumeUi("SD card", true, listOf(
            FolderUi("", "/storage/1A2B-3C4D", true, true),
            FolderUi("Notes", "/storage/1A2B-3C4D/Notes", false, true),
        ), refreshNote = "No new folders"),
    )

    private object NoIndex : IndexActions {
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

    private object NoEngine : EngineActions {
        override fun refresh() {}
        override fun verify() {}
        override fun search(query: String) {}
        override fun chooseThreads(count: Int) {}
        override fun benchmark() {}
    }
}
