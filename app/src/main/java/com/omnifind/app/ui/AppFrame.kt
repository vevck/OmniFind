package com.omnifind.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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

/** Every screen: the OmniFind header on the grey page, the screen, the tab bar. */
@Composable
fun AppFrame(tab: Tab, onTab: (Tab) -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(OmniColors.Background)) {
        Box(Modifier.fillMaxWidth().padding(start = OmniSpace.Margin, end = OmniSpace.Margin, top = 15.dp)) { Wordmark() }
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        BottomBar(Tab.entries.map { it.item }, tab.ordinal, { onTab(Tab.entries[it]) })
    }
}
