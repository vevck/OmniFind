package com.omnifind.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.omnifind.app.index.FileKind
import com.omnifind.app.index.SegmentKind
import com.omnifind.app.index.Volumes
import com.omnifind.app.search.SearchHit
import com.omnifind.app.ui.theme.CardShape
import com.omnifind.app.ui.theme.FilterPill
import com.omnifind.app.ui.theme.IconRow
import com.omnifind.app.ui.theme.OmniColors
import com.omnifind.app.ui.theme.OmniIcons
import com.omnifind.app.ui.theme.OmniSpace
import com.omnifind.app.ui.theme.OmniType
import com.omnifind.app.ui.theme.RowsCard
import com.omnifind.app.ui.theme.SearchField
import com.omnifind.app.ui.theme.SectionLabel
import java.io.File

/** What the search screen shows. */
data class SearchUi(
    val query: String = "",
    val filter: SearchViewModel.Filter = SearchViewModel.Filter.ALL,
    val hits: List<SearchHit> = emptyList(),
    val strong: Int = 0,
    val thinking: Boolean = false,
    val status: String? = null,
)

@Composable
fun SearchScreen(model: SearchViewModel, onOpen: (SearchHit) -> Unit) {
    SearchContent(
        SearchUi(model.query, model.filter, model.hits, model.strong, model.thinking, model.status),
        onQuery = model::type, onFilter = model::choose, onOpen = onOpen,
    )
}

/** The examples under "Try asking", with the icon of the kind each finds. */
private val examples = listOf(
    OmniIcons.File to "the clause about pets in the lease",
    OmniIcons.Image to "a dog on the beach",
    OmniIcons.Phone to "screenshot of a payment error",
    OmniIcons.Receipt to "invoice INV-2026-0417",
)

@Composable
fun SearchContent(
    state: SearchUi,
    onQuery: (String) -> Unit,
    onFilter: (SearchViewModel.Filter) -> Unit,
    onOpen: (SearchHit) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = OmniSpace.Margin)) {
        Spacer(Modifier.height(24.dp))
        SearchField(state.query, onQuery, highlight = true)
        Spacer(Modifier.height(19.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (filter in SearchViewModel.Filter.entries) {
                FilterPill(filter.label, state.filter == filter) { onFilter(filter) }
            }
        }
        if (state.thinking) {
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(OmniColors.PrimarySoft)) {
                Box(Modifier.fillMaxWidth(0.4f).height(3.dp).background(OmniColors.Primary))
            }
        }
        state.status?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = OmniType.BodySmall)
        }
        when {
            state.query.isBlank() -> Welcome(onQuery)
            state.hits.isEmpty() && !state.thinking -> Text(
                "Nothing found. Try other words, or describe what's in it.",
                style = OmniType.Body, modifier = Modifier.padding(top = 28.dp),
            )
            state.filter == SearchViewModel.Filter.PICTURES -> Grid(state.hits, onOpen)
            else -> Results(state, onOpen)
        }
    }
}

@Composable
private fun Welcome(onExample: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(28.5.dp))
            Text("Find anything.", style = OmniType.Hero)
            Text("On your device.", style = OmniType.HeroSoft)
            Spacer(Modifier.height(13.5.dp))
            Text("Search your documents, PDFs, photos and screenshots by what's in them.", style = OmniType.Body)
            Spacer(Modifier.height(22.dp))
            SectionLabel("Try asking")
            Spacer(Modifier.height(10.dp))
            RowsCard(rows = examples.map { (icon, text) -> { IconRow(icon, text, onClick = { onExample(text) }) } })
            Spacer(Modifier.height(20.dp))
        }
        Privacy()
        Spacer(Modifier.height(20.dp))
    }
}

/** "Searches run on this phone. Nothing leaves it." */
@Composable
private fun Privacy() {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(OmniColors.PrimarySoft)
            .padding(horizontal = 16.dp, vertical = 12.75.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(OmniIcons.Lock, null, tint = OmniColors.PrimaryInk, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text("Searches run on this phone. Nothing leaves it.", style = OmniType.Note)
    }
}

@Composable
private fun Results(state: SearchUi, onOpen: (SearchHit) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(top = 16.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(state.hits, key = { _, hit -> hit.path }) { index, hit ->
            if (index == state.strong && index > 0) {
                SectionLabel("Weaker matches", Modifier.padding(top = 12.dp, bottom = 4.dp))
            }
            Result(hit) { onOpen(hit) }
        }
    }
}

/** A small, upright, cached thumbnail (Coil reads EXIF orientation and decodes HEIC through Android). */
@Composable
private fun Thumbnail(path: String, modifier: Modifier, pixels: Int) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context).data(File(path)).size(Size(pixels, pixels)).build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(RoundedCornerShape(12.dp)).background(OmniColors.Track),
    )
}

@Composable
private fun Grid(hits: List<SearchHit>, onOpen: (SearchHit) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(hits, key = { it.path }) { hit ->
            Box(Modifier.aspectRatio(1f).clickable { onOpen(hit) }) {
                Thumbnail(hit.path, Modifier.fillMaxSize(), pixels = 320)
            }
        }
    }
}

/** A result: white card, thumbnail for pictures or the kind's icon, name, where it matched, the words, the folder. */
@Composable
private fun Result(hit: SearchHit, onClick: () -> Unit) {
    val file = File(hit.path)
    Row(
        Modifier.fillMaxWidth().clip(CardShape).background(OmniColors.Surface).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (hit.kind.isPicture) {
            Thumbnail(hit.path, Modifier.size(56.dp), pixels = 168)
        } else {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(OmniColors.PrimarySoft),
                contentAlignment = Alignment.Center) {
                Icon(if (hit.kind == FileKind.PDF) OmniIcons.Receipt else OmniIcons.File, null,
                    tint = OmniColors.Primary, modifier = Modifier.size(22.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(file.name, style = OmniType.RowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(place(hit), style = OmniType.RowSubtitle.copy(color = OmniColors.Primary, fontWeight = FontWeight(600)))
            hit.excerpt?.let {
                Text(marked(it), style = OmniType.BodySmall.copy(color = OmniColors.Body), maxLines = 3,
                    overflow = TextOverflow.Ellipsis)
            }
            val folder = Volumes.relative(file.parent ?: "").ifEmpty { "/" }
            Text(if (Volumes.onCard(file.path)) "SD card · $folder" else folder, style = OmniType.RowSubtitle,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun place(hit: SearchHit): String = when {
    hit.kind == FileKind.PDF && hit.segment == SegmentKind.PAGE && hit.loc != null -> "PDF · page ${hit.loc}"
    hit.kind == FileKind.PDF -> "PDF"
    hit.kind.isPicture -> listOfNotNull(
        if (hit.kind == FileKind.SCREENSHOT) "Screenshot" else "Photo",
        hit.taken,
        if (hit.width != null && hit.height != null) "${hit.width} × ${hit.height}" else null,
    ).joinToString(" · ")
    else -> File(hit.path).extension.uppercase()
}

/** «words» as bold, in ink. */
private fun marked(text: String): AnnotatedString = buildAnnotatedString {
    var rest = text
    while (true) {
        val start = rest.indexOf('«')
        val end = if (start >= 0) rest.indexOf('»', start) else -1
        if (start < 0 || end < 0) {
            append(rest)
            break
        }
        append(rest.substring(0, start))
        withStyle(SpanStyle(fontWeight = FontWeight(700), color = OmniColors.Ink)) { append(rest.substring(start + 1, end)) }
        rest = rest.substring(end + 1)
    }
}
