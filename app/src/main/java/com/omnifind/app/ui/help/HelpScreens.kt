package com.omnifind.app.ui.help

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.omnifind.app.help.Guide
import com.omnifind.app.help.HelpSearch
import com.omnifind.app.help.ReleaseNotes
import com.omnifind.app.ui.MaxContentWidth
import com.omnifind.app.ui.theme.CardShape
import com.omnifind.app.ui.theme.LogoTile
import com.omnifind.app.ui.theme.OmniColors
import com.omnifind.app.ui.theme.OmniIcons
import com.omnifind.app.ui.theme.OmniSpace
import com.omnifind.app.ui.theme.OmniType
import com.omnifind.app.ui.theme.RowsCard
import com.omnifind.app.ui.theme.SectionLabel
import kotlinx.coroutines.launch

/** Help's two parts. */
enum class HelpTab(val label: String) { GUIDE("User Guide"), NOTES("Release Notes") }

private val Title = OmniType.CardTitle.copy(fontSize = 20.sp)

/** Back, the logo and a title: the top of every Help screen. White with a hairline under it on an article. */
@Composable
fun HelpTopBar(title: String, onBack: () -> Unit, white: Boolean = false, titleSize: Int = 20, trailing: @Composable () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().background(if (white) OmniColors.Surface else OmniColors.Background)) {
        Row(
            Modifier.fillMaxWidth().widthIn(max = MaxContentWidth).heightIn(min = 64.dp).padding(start = 10.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack)
                    .semantics { contentDescription = "Back" },
                contentAlignment = Alignment.Center,
            ) { Icon(OmniIcons.Back, null, tint = OmniColors.Primary, modifier = Modifier.size(26.dp)) }
            Spacer(Modifier.width(4.dp))
            LogoTile(30.dp)
            Spacer(Modifier.width(13.dp))
            Text(title, style = Title.copy(fontSize = titleSize.sp), modifier = Modifier.weight(1f))
            trailing()
        }
        if (white) Box(Modifier.fillMaxWidth().height(1.dp).background(OmniColors.Divider))
    }
}

// ------------------------------------------------------------------------------------------------ Help (landing)

/**
 * Help: search over the guide and the release notes, then either the User Guide (Start here, then articles by
 * section) or the Release Notes (the latest version in full, earlier ones as a list). [guideState] and [notesState]
 * are kept by the caller, so coming back from an article lands where the person left.
 */
@Composable
fun HelpScreen(
    guide: Guide, notes: ReleaseNotes,
    tab: HelpTab, onTab: (HelpTab) -> Unit, query: String, onQuery: (String) -> Unit,
    onArticle: (String) -> Unit, onVersion: (String) -> Unit, onBack: () -> Unit,
    guideState: LazyListState = rememberLazyListState(), notesState: LazyListState = rememberLazyListState(),
) {
    BackHandler(onBack = onBack)
    Column(
        Modifier.fillMaxSize().background(OmniColors.Background).windowInsetsPadding(WindowInsets.safeDrawing),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HelpTopBar("Help", onBack)
        Column(Modifier.widthIn(max = MaxContentWidth).fillMaxWidth().padding(horizontal = OmniSpace.Margin)) {
            Spacer(Modifier.height(4.dp))
            HelpSearchField(query, onQuery)
            Spacer(Modifier.height(12.dp))
            Tabs(tab, onTab)
        }
        Box(Modifier.weight(1f).widthIn(max = MaxContentWidth).fillMaxWidth()) {
            when (tab) {
                HelpTab.GUIDE -> GuideList(guide, query, onArticle, guideState)
                HelpTab.NOTES -> NotesList(notes, query, onVersion, notesState)
            }
        }
    }
}

@Composable
private fun HelpSearchField(query: String, onQuery: (String) -> Unit) {
    BasicTextField(
        value = query, onValueChange = onQuery, singleLine = true, textStyle = OmniType.Field,
        cursorBrush = SolidColor(OmniColors.Primary),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp)).background(OmniColors.Track),
        decorationBox = { inner ->
            Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(OmniIcons.Search, null, tint = OmniColors.Muted, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(14.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search the guide and release notes", style = OmniType.Field.copy(color = OmniColors.Placeholder), maxLines = 1)
                    inner()
                }
            }
        },
    )
}

@Composable
private fun Tabs(tab: HelpTab, onTab: (HelpTab) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp)).background(OmniColors.Track).padding(4.dp)) {
        for (item in HelpTab.entries) {
            val chosen = item == tab
            Box(
                Modifier.weight(1f).heightIn(min = 40.dp)
                    .then(if (chosen) Modifier.shadow(3.dp, RoundedCornerShape(12.dp)) else Modifier)
                    .clip(RoundedCornerShape(12.dp)).background(if (chosen) OmniColors.Surface else Color.Transparent)
                    .clickable(role = Role.Tab) { onTab(item) },
                contentAlignment = Alignment.Center,
            ) {
                Text(item.label, style = OmniType.Button.copy(color = if (chosen) OmniColors.PrimaryInk else OmniColors.Muted),
                    textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun GuideList(guide: Guide, query: String, onArticle: (String) -> Unit, state: LazyListState) {
    val found = HelpSearch.articles(guide, query).map { it.id }.toSet()
    LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
        item { Spacer(Modifier.height(16.dp)) }
        val start = guide.article(guide.start)
        if (start != null && start.id in found) {
            item { StartHere(start) { onArticle(start.id) } }
        }
        for (section in guide.sections) {
            val articles = section.articles.mapNotNull(guide::article).filter { it.id in found }
            if (articles.isEmpty()) continue
            item {
                SectionLabel(section.title, Modifier.padding(start = OmniSpace.Margin + 4.dp, top = 22.dp, bottom = 10.dp))
            }
            item {
                RowsCard(Modifier.padding(horizontal = OmniSpace.Margin), rows = articles.map { article ->
                    { NavRow(title = article.title, icon = OmniIcons.Article) { onArticle(article.id) } }
                })
            }
        }
        if (found.isEmpty()) item { Nothing(query) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun StartHere(article: Guide.Article, onClick: () -> Unit) {
    Row(
        Modifier.padding(horizontal = OmniSpace.Margin).fillMaxWidth().clip(CardShape).background(OmniColors.Primary)
            .clickable(onClick = onClick).padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("START HERE", style = OmniType.SectionLabel.copy(color = OmniColors.Accent, fontWeight = FontWeight(700)))
            Spacer(Modifier.height(6.dp))
            Text(article.title, style = OmniType.CardTitle.copy(color = OmniColors.Surface, fontSize = 17.5.sp, lineHeight = 23.sp))
            Spacer(Modifier.height(6.dp))
            Text("${article.minutes} min read", style = OmniType.BodySmall.copy(color = OmniColors.OnPrimarySoft, fontSize = 13.sp))
        }
        Icon(OmniIcons.ChevronRight, null, tint = OmniColors.Surface, modifier = Modifier.size(26.dp))
    }
}

/** A row that opens something: an icon on soft blue (or none), a title and maybe a subtitle, a chevron. */
@Composable
private fun NavRow(title: String, subtitle: String? = null, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 57.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(OmniColors.PrimarySoft), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = OmniColors.Primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(15.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = if (subtitle != null) OmniType.RowTitle.copy(fontSize = 15.sp) else OmniType.RowText.copy(fontSize = 15.sp, fontWeight = FontWeight(400)))
            if (subtitle != null) Text(subtitle, style = OmniType.RowSubtitle.copy(fontSize = 12.5.sp), maxLines = 2)
        }
        Spacer(Modifier.width(8.dp))
        Icon(OmniIcons.ChevronRight, null, tint = OmniColors.Muted, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Nothing(query: String) {
    Text("Nothing in Help matches “$query”.", style = OmniType.Body,
        modifier = Modifier.padding(horizontal = OmniSpace.Margin, vertical = 24.dp))
}

// ------------------------------------------------------------------------------------------------ Release Notes

@Composable
private fun NotesList(notes: ReleaseNotes, query: String, onVersion: (String) -> Unit, state: LazyListState) {
    val found = HelpSearch.versions(notes, query)
    val latest = notes.latest
    LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
        item { Spacer(Modifier.height(16.dp)) }
        if (latest != null && latest in found) item { VersionCard(latest, isLatest = true) }
        val earlier = found.filter { it != latest }
        if (earlier.isNotEmpty()) {
            item { SectionLabel("Earlier versions", Modifier.padding(start = OmniSpace.Margin + 4.dp, top = 22.dp, bottom = 12.dp)) }
            item {
                RowsCard(Modifier.padding(horizontal = OmniSpace.Margin), rows = earlier.map { version ->
                    { NavRow("Version ${version.version}", "${version.date} · ${version.summary}") { onVersion(version.version) } }
                })
            }
        }
        if (found.isEmpty()) item { Nothing(query) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** One version's notes: its number, Latest (for the newest), its date, then New, Improved and Fixed. */
@Composable
fun VersionCard(version: ReleaseNotes.Version, isLatest: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(horizontal = OmniSpace.Margin).fillMaxWidth().clip(CardShape).background(OmniColors.Surface)
            .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 16.dp),
    ) {
        VersionHeader(
            { Text("Version ${version.version}", style = Title) },
            {
                if (isLatest) Box(Modifier.padding(start = 10.dp).clip(CircleShape).background(OmniColors.AccentHalo).padding(horizontal = 11.dp, vertical = 5.dp)) {
                    Text("Latest", style = OmniType.Tag.copy(color = Color(0xFF13804F), fontWeight = FontWeight(600)))
                }
            },
            { Text(version.date, style = OmniType.BodySmall.copy(fontSize = 14.sp)) },
        )
        if (!isLatest) Text("Build ${version.build}", style = OmniType.RowSubtitle)
        NoteList("New", version.new)
        NoteList("Improved", version.improved)
        NoteList("Fixed", version.fixed)
        version.thanks?.let {
            Spacer(Modifier.height(14.dp))
            Text(it, style = OmniType.BodySmall.copy(color = OmniColors.Body, fontSize = 14.sp, lineHeight = 21.sp))
        }
    }
}

/**
 * The version, Latest and the date on one line with the date at the right, as in the design; when a long version
 * number leaves no room, the date goes under it instead of squeezing the version onto several lines.
 */
@Composable
private fun VersionHeader(title: @Composable () -> Unit, badge: @Composable () -> Unit, date: @Composable () -> Unit) {
    Layout(contents = listOf(title, badge, date)) { (t, b, d), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val tp = t.first().measure(loose)
        val bp = b.firstOrNull()?.measure(loose.copy(maxWidth = (loose.maxWidth - tp.width).coerceAtLeast(0)))
        val dp = d.first().measure(loose)
        val first = tp.width + (bp?.width ?: 0)
        val oneLine = first + 12.dp.roundToPx() + dp.width <= constraints.maxWidth
        val top = maxOf(tp.height, bp?.height ?: 0)
        val height = if (oneLine) maxOf(top, dp.height) else top + 4.dp.roundToPx() + dp.height
        layout(constraints.maxWidth, height) {
            val line = if (oneLine) height else top
            tp.place(0, (line - tp.height) / 2)
            bp?.place(tp.width, (line - bp.height) / 2)
            if (oneLine) dp.place(constraints.maxWidth - dp.width, (line - dp.height) / 2)
            else dp.place(0, top + 4.dp.roundToPx())
        }
    }
}

@Composable
private fun NoteList(label: String, items: List<String>) {
    if (items.isEmpty()) return
    Spacer(Modifier.height(16.dp))
    Text(label.uppercase(), style = OmniType.SectionLabel.copy(color = OmniColors.PrimaryInk, fontWeight = FontWeight(700)))
    Spacer(Modifier.height(6.dp))
    for (item in items) {
        Row(Modifier.padding(vertical = 3.dp)) {
            Box(Modifier.width(22.dp).height(with(LocalDensity.current) { NoteText.lineHeight.toDp() }), contentAlignment = Alignment.Center) {
                Box(Modifier.size(5.dp).clip(CircleShape).background(OmniColors.Body))
            }
            Text(item, style = NoteText)
        }
    }
}

private val NoteText = OmniType.Body.copy(fontSize = 15.5.sp, lineHeight = 22.5.sp)

/** An earlier version's notes on a page of their own. */
@Composable
fun VersionScreen(version: ReleaseNotes.Version, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(
        Modifier.fillMaxSize().background(OmniColors.Background).windowInsetsPadding(WindowInsets.safeDrawing),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HelpTopBar("Release Notes", onBack)
        Column(Modifier.widthIn(max = MaxContentWidth).fillMaxWidth().verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(8.dp))
            VersionCard(version, isLatest = false)
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ------------------------------------------------------------------------------------------------ Article

/** Article text sizes the Aa button steps through. */
val TextScales = listOf(1f, 1.15f, 1.3f)

/**
 * A guide article: its section, title and reading time, "On this page" chips that scroll to each heading, then the
 * text and figures (real screenshots in a phone frame; tap one to see it full size). Aa makes the text larger.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ArticleScreen(
    article: Guide.Article, updated: String, figure: (String) -> ImageBitmap?,
    textScale: Float, onTextScale: (Float) -> Unit, onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val anchorY = remember { mutableStateMapOf<String, Int>() }
    var enlarged by rememberSaveable { mutableStateOf<String?>(null) }
    val body = OmniType.Body.copy(fontSize = 16.sp * textScale, lineHeight = 26.sp * textScale, color = OmniColors.Body)
    Column(
        Modifier.fillMaxSize().background(OmniColors.Surface).windowInsetsPadding(WindowInsets.safeDrawing),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HelpTopBar("User Guide", onBack, white = true, titleSize = 18) {
            Box(
                Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) {
                    onTextScale(TextScales[(TextScales.indexOf(textScale) + 1).mod(TextScales.size)])
                }.padding(horizontal = 8.dp, vertical = 6.dp).semantics { contentDescription = "Text size" },
            ) { Text("Aa", style = Title.copy(color = OmniColors.Primary, fontSize = 18.sp)) }
        }
        Column(
            Modifier.weight(1f).widthIn(max = MaxContentWidth).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 22.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text(article.section.uppercase(), style = OmniType.SectionLabel.copy(color = OmniColors.PrimaryInk, fontSize = 12.sp, fontWeight = FontWeight(700)))
            Spacer(Modifier.height(6.dp))
            Text(article.title, style = OmniType.ScreenTitle.copy(fontSize = 27.5.sp * textScale, lineHeight = 33.sp * textScale))
            Spacer(Modifier.height(6.dp))
            Text("${article.minutes} min read · Updated $updated", style = OmniType.BodySmall.copy(fontSize = 14.sp))
            if (article.anchors.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                SectionLabel("On this page")
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (anchor in article.anchors) {
                        val label = anchor.anchor ?: anchor.text ?: continue
                        Box(
                            Modifier.clip(CircleShape).background(OmniColors.PrimarySoft)
                                .clickable { anchorY[label]?.let { y -> scope.launch { scroll.animateScrollTo(y) } } }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) { Text(label, style = OmniType.Button.copy(color = OmniColors.PrimaryInk, fontSize = 14.sp)) }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            for (block in article.blocks) {
                when (block.type) {
                    "h" -> Text(
                        block.text.orEmpty(),
                        style = OmniType.CardTitle.copy(fontSize = 20.sp * textScale, lineHeight = 26.sp * textScale),
                        modifier = Modifier.padding(top = 18.dp, bottom = 6.dp).onGloballyPositioned {
                            anchorY[block.anchor ?: block.text.orEmpty()] = it.positionInParent().y.toInt()
                        },
                    )
                    "figure" -> Figure(block, figure(block.image.orEmpty())) { enlarged = block.image }
                    else -> Text(marked(block.text.orEmpty()), style = body, modifier = Modifier.padding(vertical = 6.dp))
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
    enlarged?.let { name ->
        Dialog(onDismissRequest = { enlarged = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f)).clickable { enlarged = null }.padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                figure(name)?.let { Image(it, "Enlarged screenshot", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
            }
        }
    }
}

/** A screenshot in a phone frame on a grey card, with its caption: tap to enlarge. */
@Composable
private fun Figure(block: Guide.Block, image: ImageBitmap?, onEnlarge: () -> Unit) {
    Column(
        Modifier.padding(vertical = 14.dp).fillMaxWidth().clip(CardShape).background(Color(0xFFF1F3F6))
            .clickable(onClick = onEnlarge).padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (image != null) {
            Box(
                Modifier.widthIn(max = 210.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .border(6.dp, OmniColors.Ink, RoundedCornerShape(24.dp)).background(OmniColors.Background).padding(10.dp),
            ) {
                Image(image, block.caption, contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)))
            }
        }
        block.caption?.let {
            Spacer(Modifier.height(10.dp))
            Text("$it Tap to enlarge.", style = OmniType.BodySmall.copy(fontSize = 13.5.sp, lineHeight = 17.sp), textAlign = TextAlign.Center)
        }
    }
}

/** **bold** in an article's text. */
private fun marked(text: String): AnnotatedString = buildAnnotatedString {
    val parts = text.split("**")
    parts.forEachIndexed { i, part ->
        if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight(700), color = OmniColors.Ink)) { append(part) } else append(part)
    }
}

// ------------------------------------------------------------------------------------------------ Placeholders

/** A page for a menu entry whose screen isn't built yet: says so plainly. */
@Composable
fun PlaceholderScreen(title: String, message: String, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(
        Modifier.fillMaxSize().background(OmniColors.Background).windowInsetsPadding(WindowInsets.safeDrawing),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HelpTopBar(title, onBack)
        Column(Modifier.widthIn(max = MaxContentWidth).fillMaxWidth().padding(OmniSpace.Margin)) {
            Column(Modifier.fillMaxWidth().clip(CardShape).background(OmniColors.Surface).padding(20.dp)) {
                Text("Coming soon", style = OmniType.CardTitle)
                Spacer(Modifier.height(8.dp))
                Text(message, style = OmniType.BodySmall.copy(color = OmniColors.Body, fontSize = 14.sp, lineHeight = 21.sp))
            }
        }
    }
}
