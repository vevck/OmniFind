package com.omnifind.app.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.omnifind.app.ui.theme.BottomBar
import com.omnifind.app.ui.theme.OmniColors
import com.omnifind.app.ui.theme.OmniIcons
import com.omnifind.app.ui.theme.OmniSpace
import com.omnifind.app.ui.theme.TabItem
import com.omnifind.app.ui.theme.Wordmark

enum class Tab(val item: TabItem) {
    SEARCH(TabItem("Search", OmniIcons.Search)),
    INDEX(TabItem("Index", OmniIcons.Folder)),
    ENGINE(TabItem("Engine", OmniIcons.Chip)),
}

/**
 * The widest the content gets: phones use their full width (320–480 dp), tablets, foldables and landscape phones get a
 * centred column instead of lines and cards stretched across the screen.
 */
val MaxContentWidth = 600.dp

/**
 * Every screen: the OmniFind header on the grey page, the screen, the tab bar. Drawn edge to edge (Android 15+ always
 * does, and the app opts in on 9–14 too): the page runs under the status bar, the tab bar's white under the navigation
 * bar, and the content keeps clear of both, of a camera cutout, and of the keyboard (the tab bar hides while typing).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppFrame(tab: Tab, onTab: (Tab) -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(OmniColors.Background)) {
        Column(
            Modifier.weight(1f).fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.widthIn(max = MaxContentWidth).fillMaxWidth()
                .padding(start = OmniSpace.Margin, end = OmniSpace.Margin, top = 15.dp)) { Wordmark() }
            Box(Modifier.weight(1f).widthIn(max = MaxContentWidth).fillMaxWidth()) { content() }
        }
        if (!WindowInsets.isImeVisible) {
            BottomBar(Tab.entries.map { it.item }, tab.ordinal, { onTab(Tab.entries[it]) })
        }
    }
}
