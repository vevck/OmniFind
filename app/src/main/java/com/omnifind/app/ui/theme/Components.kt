package com.omnifind.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A label that shrinks to fit (down to 70%) instead of being cut off: buttons and chips on a 320 dp screen (ldpi), or
 * at a large font size. Past that it wraps to a second line rather than losing words.
 */
@Composable
fun FitText(
    text: String, style: TextStyle, modifier: Modifier = Modifier, textAlign: TextAlign = TextAlign.Center,
    maxLines: Int = 2,
) {
    val max = style.fontSize
    BasicText(
        text, modifier, style = style.copy(textAlign = textAlign), maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = max * 0.7f, maxFontSize = max, stepSize = 0.25.sp),
    )
}

/** Spacing from the mockups: 20 dp screen margins and card padding. */
object OmniSpace {
    val Margin = 20.dp
    val CardPadding = 20.dp
}

val CardShape = RoundedCornerShape(22.dp)
val FieldShape = RoundedCornerShape(16.dp)

/** A white card on the grey page. */
@Composable
fun OmniCard(
    modifier: Modifier = Modifier,
    color: Color = OmniColors.Surface,
    padding: PaddingValues = PaddingValues(OmniSpace.CardPadding),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().clip(CardShape).background(color).padding(padding), content = content)
}

/** A card of rows with hairline dividers between them (examples, file types, folders). */
@Composable
fun RowsCard(modifier: Modifier = Modifier, rows: List<@Composable () -> Unit>) {
    Column(modifier.fillMaxWidth().clip(CardShape).background(OmniColors.Surface)) {
        rows.forEachIndexed { index, row ->
            if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(OmniColors.Divider))
            row()
        }
    }
}

/** UPPERCASE, letter-spaced, muted: the label above a section. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = OmniType.SectionLabel, modifier = modifier)
}

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = OmniType.ScreenTitle, modifier = modifier)
}

/** A pill button: filled blue, or outlined in blue on white. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    enabled: Boolean = true,
    height: Dp = 48.dp,
    textStyle: TextStyle = OmniType.Button,
) {
    val shape = CircleShape
    val base = modifier.heightIn(min = height).clip(shape)
    val styled = if (filled) base.background(if (enabled) OmniColors.Primary else OmniColors.Primary.copy(alpha = 0.4f))
    else base.background(OmniColors.Surface).border(BorderStroke(2.dp, OmniColors.Primary.copy(alpha = if (enabled) 1f else 0.4f)), shape)
    Box(
        styled.clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        FitText(
            text,
            textStyle.copy(color = if (filled) OmniColors.Surface else OmniColors.Primary.copy(alpha = if (enabled) 1f else 0.4f)),
        )
    }
}

/** A filter chip: filled blue when chosen, outlined grey otherwise. */
@Composable
fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = CircleShape
    Box(
        Modifier.heightIn(min = 40.dp).clip(shape)
            .background(if (selected) OmniColors.Primary else OmniColors.Surface)
            .then(if (selected) Modifier else Modifier.border(1.dp, OmniColors.Border, shape))
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = if (selected) 18.dp else 19.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = OmniType.Chip.copy(color = if (selected) OmniColors.Surface else OmniColors.Ink,
            fontWeight = androidx.compose.ui.text.font.FontWeight(if (selected) 700 else 500)))
    }
}

/** A soft blue tag (what the model reads: Text, Images, Audio). */
@Composable
fun Tag(text: String) {
    Box(
        Modifier.heightIn(min = 28.dp).clip(CircleShape).background(OmniColors.PrimarySoft).padding(horizontal = 12.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = OmniType.Tag, maxLines = 1, softWrap = false) }
}

/** The search field: white, a leading search icon, blue outline while focused (with a soft glow). */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Describe what you're looking for",
    height: Dp = 56.dp,
    highlight: Boolean = false,
    onSearch: () -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val active = highlight || focused
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = OmniType.Field,
        cursorBrush = SolidColor(OmniColors.Primary),
        interactionSource = interaction,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        modifier = modifier.fillMaxWidth().heightIn(min = height)
            .then(if (active) Modifier.shadow(10.dp, FieldShape, ambientColor = OmniColors.Primary, spotColor = OmniColors.Primary.copy(alpha = 0.35f)) else Modifier)
            .clip(FieldShape).background(OmniColors.Surface)
            .border(if (active) 2.dp else 1.5.dp, if (active) OmniColors.Primary else OmniColors.Border, FieldShape),
        decorationBox = { inner ->
            Row(Modifier.heightIn(min = height).padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(OmniIcons.Search, null, tint = if (active) OmniColors.Primary else OmniColors.Muted, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(14.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) FitText(placeholder, OmniType.Field.copy(color = OmniColors.Placeholder),
                        textAlign = TextAlign.Start, maxLines = 1)
                    inner()
                }
            }
        },
    )
}

/** A square checkbox: 2 dp grey outline, or filled blue with a white tick. */
@Composable
fun OmniCheckbox(checked: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(3.5.dp)
    Box(
        modifier.size(22.dp).clip(shape)
            .background(if (checked) OmniColors.Primary else OmniColors.Surface)
            .then(if (checked) Modifier else Modifier.border(2.dp, OmniColors.CheckboxBorder, shape)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(OmniIcons.Check, null, tint = OmniColors.Surface, modifier = Modifier.size(20.dp))
    }
}

/** A row with a checkbox, a title and an optional subtitle; the whole row toggles. */
@Composable
fun CheckRow(
    title: String, subtitle: String?, checked: Boolean, onToggle: () -> Unit, minHeight: Dp = 60.dp,
    badge: String? = null,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = minHeight).clickable(role = Role.Checkbox, onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OmniCheckbox(checked)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)) {
            Text(title, style = OmniType.RowTitle, maxLines = 2)
            if (subtitle != null) Text(subtitle, style = OmniType.RowSubtitle, maxLines = 2)
        }
        if (badge != null) {
            Spacer(Modifier.width(8.dp))
            Tag(badge)
        }
    }
}

/** Options side by side on a grey track, the chosen one in blue. */
@Composable
fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier, height: Dp = 52.dp) {
    Row(
        modifier.fillMaxWidth().heightIn(min = height).height(IntrinsicSize.Min).clip(RoundedCornerShape(15.dp)).background(OmniColors.Track).padding(4.dp),
    ) {
        for (option in options) {
            val chosen = option == selected
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(11.dp))
                    .background(if (chosen) OmniColors.Primary else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                FitText(label(option), OmniType.Field.copy(
                    color = if (chosen) OmniColors.Surface else OmniColors.Ink,
                    fontWeight = androidx.compose.ui.text.font.FontWeight(if (chosen) 700 else 400),
                ), Modifier.padding(horizontal = 6.dp, vertical = 8.dp))
            }
        }
    }
}

/** A small text button with an icon (Refresh), in the brand blue. */
@Composable
fun SmallAction(text: String, icon: ImageVector, onClick: () -> Unit, enabled: Boolean = true) {
    Row(
        Modifier.clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 40.dp).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = OmniColors.Primary.copy(alpha = if (enabled) 1f else 0.4f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = OmniType.ButtonSmall.copy(color = OmniColors.Primary.copy(alpha = if (enabled) 1f else 0.4f)))
    }
}

/** One tab of the bottom bar. */
data class TabItem(val label: String, val icon: ImageVector)

/** The bottom bar: white, a hairline on top, the chosen tab's icon on a soft blue pill and its label in blue. */
@Composable
fun BottomBar(items: List<TabItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(OmniColors.Surface).windowInsetsPadding(WindowInsets.navigationBars)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(OmniColors.Track))
        // On a tablet the tabs stay together in the middle rather than spreading across the screen.
        Row(Modifier.fillMaxWidth().wrapContentWidth().widthIn(max = 600.dp).heightIn(min = 83.dp).height(IntrinsicSize.Min)) {
            items.forEachIndexed { index, item ->
                val chosen = index == selected
                Column(
                    Modifier.weight(1f).fillMaxHeight()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Tab) { onSelect(index) }
                        .padding(top = 10.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(64.dp, 32.dp).clip(CircleShape)
                            .background(if (chosen) OmniColors.TabIndicator else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(item.icon, item.label, tint = if (chosen) OmniColors.Primary else OmniColors.Muted, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.height(3.5.dp))
                    Text(item.label, style = OmniType.TabLabel.copy(
                        color = if (chosen) OmniColors.PrimaryInk else OmniColors.Muted,
                        fontWeight = androidx.compose.ui.text.font.FontWeight(if (chosen) 700 else 500),
                    ))
                }
            }
        }
    }
}

/** A green "ready" dot in a pale halo (grey when not ready). */
@Composable
fun StatusDot(ready: Boolean) {
    val halo = if (ready) OmniColors.AccentHalo else OmniColors.Track
    Box(
        Modifier.size(13.dp).drawBehind { drawCircle(halo, radius = 10.5.dp.toPx()) }
            .clip(CircleShape).background(if (ready) OmniColors.Accent else OmniColors.Muted),
    )
}

/** A thin progress bar: green on a translucent track (for the blue hero card). */
@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier, track: Color = Color.White.copy(alpha = 0.22f)) {
    Box(modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(track)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(OmniColors.Accent))
    }
}

/** A row with an icon and text, for lists of examples and settings. */
@Composable
fun IconRow(icon: ImageVector, text: String, onClick: (() -> Unit)? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 55.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = OmniColors.Primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = OmniType.RowText, modifier = Modifier.weight(1f), maxLines = 2)
        trailing()
    }
}
