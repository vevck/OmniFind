package com.omnifind.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnifind.app.ui.theme.OmniColors
import com.omnifind.app.ui.theme.OmniIcons
import com.omnifind.app.ui.theme.OmniType

/** Where the menu's entries lead. */
enum class MenuItem { SETTINGS, DASHBOARD, HELP }

/** The ⋮ button at the right of every screen's header; on a soft blue square while its menu is open. */
@Composable
fun MenuButton(open: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
            .background(if (open) OmniColors.TabIndicator else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (open) "Close menu" else "Menu" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(OmniIcons.Kebab, null, tint = OmniColors.Primary, modifier = Modifier.size(24.dp))
    }
}

/**
 * The menu over the page, as in the design: the page dims (ink at 18%), and a white card hangs below the ⋮ button
 * with Settings, Dashboard (PRO) and, after a divider, Help. Tapping outside or Back closes it.
 */
@Composable
fun MenuFlyout(onPick: (MenuItem) -> Unit, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    Box(
        Modifier.fillMaxSize().background(OmniColors.Ink.copy(alpha = 0.18f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        Column(
            Modifier.windowInsetsPadding(WindowInsets.safeDrawing).align(Alignment.TopEnd)
                .padding(top = 58.dp, end = 12.dp).widthIn(min = 248.dp, max = 340.dp).width(IntrinsicSize.Max)
                .shadow(18.dp, RoundedCornerShape(20.dp), ambientColor = OmniColors.Ink.copy(alpha = 0.25f),
                    spotColor = OmniColors.Ink.copy(alpha = 0.25f))
                .clip(RoundedCornerShape(20.dp)).background(OmniColors.Surface)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(vertical = 8.dp),
        ) {
            Item(OmniIcons.Sliders, "Settings") { onPick(MenuItem.SETTINGS) }
            Item(OmniIcons.Grid, "Dashboard", badge = "PRO") { onPick(MenuItem.DASHBOARD) }
            Box(Modifier.padding(horizontal = 18.dp, vertical = 6.dp).fillMaxWidth().height(1.dp).background(OmniColors.Divider))
            Item(OmniIcons.Help, "Help") { onPick(MenuItem.HELP) }
        }
    }
}

@Composable
private fun Item(icon: ImageVector, label: String, badge: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = OmniColors.Primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(13.dp))
        Text(label, style = OmniType.RowText.copy(fontSize = 16.sp, fontWeight = FontWeight(500)), modifier = Modifier.weight(1f))
        if (badge != null) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.clip(CircleShape).background(OmniColors.PrimarySoft).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text(badge, style = OmniType.Tag.copy(fontSize = 12.sp, fontWeight = FontWeight(700), letterSpacing = 1.sp))
            }
        }
    }
}

