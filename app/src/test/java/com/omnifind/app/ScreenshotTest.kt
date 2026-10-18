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
import com.omnifind.app.ui.IndexActions
import com.omnifind.app.ui.IndexContent
import com.omnifind.app.ui.IndexType
import com.omnifind.app.ui.IndexUi
import com.omnifind.app.ui.SearchContent
import com.omnifind.app.ui.SearchUi
import com.omnifind.app.ui.Tab
import com.omnifind.app.ui.theme.OmniTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Calendar

/**
 * The three screens as the design mockups show them, rendered on the Mac at the mockups' size (390 × 844 dp at 2×:
 * 780 × 1688 px) into app/build/screens/, to compare with them (tools/ux/compare.py).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-xhdpi", application = Application::class)
class ScreenshotTest {
    private fun shot(name: String, tab: Tab, content: @Composable () -> Unit) =
        captureRoboImage("build/screens/$name.png") {
            OmniTheme { AppFrame(tab, {}) { content() } }
        }

    @Test
    fun search() = shot("search", Tab.SEARCH) { SearchContent(SearchUi(), {}, {}, {}) }

    @Test
    fun index() = shot("index", Tab.INDEX) {
        val stats = IndexStore.Stats(
            files = 57, done = 57, failed = 0, skipped = 0, pieces = 0, embedded = 0, waiting = 0,
            picturesWaiting = emptyMap(), picturesEmbedded = 57, byKind = mapOf(FileKind.IMAGE to 57),
        )
        val at = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 18, 20, 49) }.timeInMillis
        IndexContent(
            IndexUi(stats = stats, lastRun = "0 new or changed · 57 skipped · 2 embedded", lastRunAt = at,
                chosen = setOf(IndexType.PICTURES)),
            NoIndexActions,
        )
    }

    @Test
    fun engine() = shot("engine", Tab.ENGINE) { EngineContent(EngineUi(), NoEngineActions) }

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
