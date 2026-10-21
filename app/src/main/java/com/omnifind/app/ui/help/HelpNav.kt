package com.omnifind.app.ui.help

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.omnifind.app.help.Guide
import com.omnifind.app.help.ReleaseNotes
import com.omnifind.app.ui.MenuItem

/**
 * The pages the menu leads to, as a back stack of routes: "help", "article/<id>", "version/<version>", "settings",
 * "dashboard". Back pops one; an empty stack is the tabs again.
 */
object HelpRoutes {
    fun of(item: MenuItem) = when (item) {
        MenuItem.HELP -> "help"
        MenuItem.SETTINGS -> "settings"
        MenuItem.DASHBOARD -> "dashboard"
    }
    fun article(id: String) = "article/$id"
    fun version(name: String) = "version/$name"
}

/** The guide and release notes from the app's assets, with the figures decoded once. */
@Composable
fun HelpHost(stack: List<String>, onStack: (List<String>) -> Unit) {
    val context = LocalContext.current
    val guide = remember { Guide.load(context) }
    val notes = remember { ReleaseNotes.load(context) }
    val figures = remember { HashMap<String, ImageBitmap?>() }
    HelpPages(stack, onStack, guide, notes) { name -> figures.getOrPut(name) { Guide.figure(context, name)?.asImageBitmap() } }
}

/**
 * Shows the top of [stack]. What the Help page holds (tab, search, each list's scroll) lives here, above the pages,
 * and each page's own state (an article's scroll) is kept by route while it's on the stack: so going from the guide
 * into an article and back lands exactly where the person was.
 */
@Composable
fun HelpPages(
    stack: List<String>, onStack: (List<String>) -> Unit,
    guide: Guide, notes: ReleaseNotes, figure: (String) -> ImageBitmap?,
) {
    val pages = rememberSaveableStateHolder()
    var tab by rememberSaveable { mutableStateOf(HelpTab.GUIDE) }
    var query by rememberSaveable { mutableStateOf("") }
    var textScale by rememberSaveable { mutableFloatStateOf(TextScales.first()) }
    val guideState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val notesState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val back = { onStack(stack.dropLast(1)) }
    val route = stack.lastOrNull() ?: return
    pages.SaveableStateProvider(route) {
        when {
            route == "help" -> HelpScreen(
                guide, notes, tab, { tab = it }, query, { query = it },
                onArticle = { onStack(stack + HelpRoutes.article(it)) },
                onVersion = { onStack(stack + HelpRoutes.version(it)) },
                onBack = back, guideState = guideState, notesState = notesState,
            )
            route.startsWith("article/") -> guide.article(route.removePrefix("article/"))
                ?.let { ArticleScreen(it, guide.updated, figure, textScale, { s -> textScale = s }, back) }
                ?: Gone(back)
            route.startsWith("version/") -> notes.version(route.removePrefix("version/"))
                ?.let { VersionScreen(it, back) } ?: Gone(back)
            route == "settings" -> PlaceholderScreen("Settings", "Settings will gather OmniFind's options in one place. " +
                "For now, indexing options are on the Index tab and the model's on the Engine tab.", back)
            route == "dashboard" -> PlaceholderScreen("Dashboard", "The Dashboard, part of OmniFind Pro, will show " +
                "what's indexed and how searches perform over time.", back)
            else -> Gone(back)
        }
    }
    // Forget a page's state once it's off the stack, so opening it again starts at the top.
    val previous = remember { mutableStateOf(stack) }
    SideEffect {
        for (gone in previous.value - stack.toSet()) pages.removeState(gone)
        previous.value = stack
    }
}

/** A route to something that isn't there (an article removed by an update): step back past it. */
@Composable
private fun Gone(back: () -> Unit) = LaunchedEffect(Unit) { back() }
