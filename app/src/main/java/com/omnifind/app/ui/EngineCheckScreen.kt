package com.omnifind.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnifind.app.engine.ModelFiles
import com.omnifind.app.ui.theme.OmniCard
import com.omnifind.app.ui.theme.OmniColors
import com.omnifind.app.ui.theme.OmniSpace
import com.omnifind.app.ui.theme.OmniType
import com.omnifind.app.ui.theme.PillButton
import com.omnifind.app.ui.theme.OmniIcons
import com.omnifind.app.ui.theme.SmallAction
import com.omnifind.app.ui.theme.ProgressLine
import com.omnifind.app.ui.theme.RowsCard
import com.omnifind.app.ui.theme.ScreenTitle
import com.omnifind.app.ui.theme.SearchField
import com.omnifind.app.ui.theme.SectionLabel
import com.omnifind.app.ui.theme.Segmented
import com.omnifind.app.ui.theme.StatusDot
import com.omnifind.app.ui.theme.Tag
import java.util.Locale

/** What the engine screen shows. */
data class EngineUi(
    val textModel: Boolean = true,
    val mediaModel: Boolean = true,
    val folder: String = "",
    val busy: String? = null,
    val results: List<Pair<String, Float>> = emptyList(),
    val threads: Int = 4,
    val log: String = "",
    val download: String? = null,
    val downloadFraction: Float? = null,
)

interface EngineActions {
    fun refresh()
    fun verify()
    fun search(query: String)
    fun chooseThreads(count: Int)
    fun benchmark()
    fun download() {}
    fun stopDownload() {}
    fun tour() {}
}

@Composable
fun EngineCheckScreen(model: EngineCheckViewModel, onTour: () -> Unit = {}) {
    val models = model.models
    EngineContent(
        EngineUi(models != null, models?.media != null, model.folder.path, model.busy, model.results, model.threads, model.log,
            model.download, model.downloadFraction),
        object : EngineActions {
            override fun refresh() = model.refresh()
            override fun verify() = model.verifyChecksums()
            override fun search(query: String) = model.search(query)
            override fun chooseThreads(count: Int) { model.threads = count }
            override fun benchmark() = model.benchmark()
            override fun download() = model.startDownload()
            override fun stopDownload() { model.stopDownload() }
            override fun tour() = onTour()
        },
    )
}

@Composable
fun EngineContent(state: EngineUi, actions: EngineActions) {
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = OmniSpace.Margin)) {
        Spacer(Modifier.height(19.dp))
        ScreenTitle("Engine")
        Spacer(Modifier.height(3.5.dp))
        Text("EmbeddingGemma 2 · running on this phone's CPU", style = OmniType.Subtitle)
        Spacer(Modifier.height(17.dp))

        OmniCard(padding = PaddingValues(start = 20.dp, end = 20.dp, top = 19.dp, bottom = 17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(ready = state.textModel)
                Spacer(Modifier.width(9.dp))
                Text(
                    when {
                        !state.textModel -> "Model not on the phone yet"
                        !state.mediaModel -> "Text model ready"
                        else -> "Model ready"
                    },
                    style = OmniType.CardTitle,
                )
            }
            if (state.textModel) {
                Spacer(Modifier.height(14.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tag("Text")
                    if (state.mediaModel) { Tag("Images"); Tag("Audio") }
                }
            }
            if (!state.mediaModel) {
                Spacer(Modifier.height(12.dp))
                if (state.download != null) {
                    Text(state.download, style = OmniType.BodySmall.copy(color = OmniColors.Ink))
                    Spacer(Modifier.height(8.dp))
                    ProgressLine(state.downloadFraction ?: 0f, track = OmniColors.Track)
                    Spacer(Modifier.height(12.dp))
                    PillButton("Stop download", actions::stopDownload, Modifier.fillMaxWidth(), filled = false, height = 44.dp,
                        textStyle = OmniType.ButtonSmall)
                } else {
                    Text("Search works by words now. For search by meaning, OmniFind needs its model: 865 MB, " +
                        "downloaded once over Wi-Fi from Hugging Face. Nothing about your files is sent.",
                        style = OmniType.BodySmall)
                    Spacer(Modifier.height(12.dp))
                    PillButton("Download the model", actions::download, Modifier.fillMaxWidth(), height = 44.dp,
                        textStyle = OmniType.ButtonSmall.copy(color = OmniColors.Surface))
                }
                Spacer(Modifier.height(8.dp))
                Text("Developers: or push ${ModelFiles.all.joinToString(" and ") { it.name }} to ${state.folder}",
                    style = OmniType.RowSubtitle.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 14.sp))
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Look again", actions::refresh, Modifier.weight(1f), filled = false, height = 44.dp,
                    textStyle = OmniType.ButtonSmall)
                PillButton("Verify files", actions::verify, Modifier.weight(1f), filled = false, height = 44.dp,
                    textStyle = OmniType.ButtonSmall, enabled = state.busy == null)
            }
        }

        Spacer(Modifier.height(15.5.dp))
        SectionLabel("Try a search")
        Spacer(Modifier.height(10.dp))
        SearchField(query, { query = it }, height = 52.dp, onSearch = { actions.search(query) })
        Spacer(Modifier.height(10.dp))
        PillButton("Search the sample sentences", { actions.search(query) }, Modifier.fillMaxWidth(), height = 50.dp,
            enabled = state.textModel && state.busy == null)
        if (state.results.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            RowsCard(rows = state.results.map { (sentence, score) ->
                {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
                        Text("%.3f".format(Locale.ROOT, score),
                            style = OmniType.RowSubtitle.copy(color = OmniColors.Primary, fontWeight = FontWeight(700)),
                            modifier = Modifier.width(48.dp))
                        Text(sentence, style = OmniType.BodySmall.copy(color = OmniColors.Body))
                    }
                }
            })
        }

        Spacer(Modifier.height(15.5.dp))
        SectionLabel("Benchmark · CPU threads")
        Spacer(Modifier.height(10.dp))
        // Tapping a thread count times passages and pictures with it (the model must be on the phone).
        Segmented(listOf(2, 4, 6, 8), state.threads, { "$it" }, { count ->
            actions.chooseThreads(count)
            if (state.mediaModel && state.busy == null) actions.benchmark()
        })

        Spacer(Modifier.height(10.dp))
        SmallAction("What's new in this build", OmniIcons.Chip, actions::tour)

        state.busy?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = OmniType.BodySmall.copy(color = OmniColors.Primary, fontWeight = FontWeight(600)))
        }
        if (state.log.isNotBlank()) {
            Spacer(Modifier.height(18.dp))
            SectionLabel("Log")
            Spacer(Modifier.height(13.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(OmniColors.Surface).padding(14.dp)) {
                Text(state.log.trim(), style = OmniType.RowSubtitle.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                    lineHeight = 16.sp, color = OmniColors.Body))
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}
