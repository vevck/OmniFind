package com.omnifind.app

import android.app.Application
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.omnifind.app.help.Guide
import com.omnifind.app.help.HelpSearch
import com.omnifind.app.help.ReleaseNotes
import com.omnifind.app.release.Release
import com.omnifind.app.ui.AppFrame
import com.omnifind.app.ui.MenuItem
import com.omnifind.app.ui.Tab
import com.omnifind.app.ui.help.HelpPages
import com.omnifind.app.ui.help.HelpRoutes
import com.omnifind.app.ui.theme.OmniTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** TC-11: the ⋮ menu, Help's guide and release notes, articles, and keeping your place between them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-xhdpi", application = Application::class)
class HelpTest {
    @get:Rule val compose = createComposeRule()

    private val guide = Guide.parse(File("src/main/assets/help/guide.json").readText())
    private val notes = ReleaseNotes.parse(File("src/main/assets/help/release_notes.json").readText())
    private val release = Release.parse(File("src/main/assets/release/release.json").readText())

    @Test
    fun guideIsComplete() {
        assertTrue("start article", guide.article(guide.start) != null)
        val listed = guide.sections.flatMap { it.articles }
        for (id in listed) assertTrue("unknown article $id", guide.article(id) != null)
        assertEquals("every article listed once", guide.articles.map { it.id }.toSet() - guide.start, listed.toSet())
        for (article in guide.articles) for (block in article.blocks.filter { it.type == "figure" }) {
            assertTrue("${article.id}: no figure ${block.image}", File("src/main/assets/help/figures/${block.image}.webp").exists())
        }
    }

    @Test
    fun releaseNotesAreThisBuildThenEveryEarlierOne() {
        assertEquals(release.version, notes.latest?.version)
        assertEquals(release.build, notes.latest?.build)
        assertTrue("0.3.0-beta.1 is there", notes.version("0.3.0-beta.1") != null)
        assertEquals("newest first", notes.versions.sortedByDescending { it.build }, notes.versions)
    }

    @Test
    fun searchFindsArticlesAndVersions() {
        assertEquals("charging-and-battery", HelpSearch.articles(guide, "pauses if it gets hot").single().id)
        assertTrue(HelpSearch.versions(notes, "DigUp").any { it.version == "0.3.0-beta.1" })
        assertTrue(HelpSearch.articles(guide, "zzz nothing").isEmpty())
    }

    @Test
    fun menuOpensAndPicksHelp() {
        var open by mutableStateOf(false)
        var picked: MenuItem? = null
        compose.setContent { OmniTheme { AppFrame(Tab.SEARCH, {}, open, { open = it }, { picked = it }) {} } }
        compose.onNodeWithContentDescription("Menu").performClick()
        compose.onNodeWithText("Dashboard").assertIsDisplayed()
        compose.onNodeWithText("Help").performClick()
        assertEquals(MenuItem.HELP, picked)
        assertEquals(false, open)
        assertEquals("help", HelpRoutes.of(MenuItem.HELP))
    }

    @Test
    fun articleAndBackKeepYourPlace() {
        var stack by mutableStateOf(listOf("help"))
        compose.setContent { OmniTheme { HelpPages(stack, { stack = it }, guide, notes, { null }) } }
        // Release Notes tab, then an earlier version, then back: still on Release Notes.
        compose.onNodeWithText("Release Notes").performClick()
        compose.onNodeWithText("Version 0.3.0-beta.1").performClick()
        assertEquals(listOf("help", "version/0.3.0-beta.1"), stack)
        compose.onNodeWithText("Thank you, Abdur Rahim", substring = true).assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("help"), stack)
        compose.onNodeWithText("Version ${release.version}").assertIsDisplayed()
        // The guide: an article, and back to the guide with the search still typed.
        compose.onNodeWithText("User Guide").performClick()
        compose.onNodeWithText("Search the guide and release notes").performTextInput("battery")
        compose.onNodeWithText("Charging and battery use").performClick()
        assertEquals(listOf("help", "article/charging-and-battery"), stack)
        compose.onNodeWithText("min read · Updated", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("battery").assertIsDisplayed()
        compose.onNodeWithText("Charging and battery use").assertIsDisplayed()
    }
}
