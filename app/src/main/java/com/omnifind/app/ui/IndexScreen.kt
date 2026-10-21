package com.omnifind.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnifind.app.index.FileKind
import com.omnifind.app.index.FolderPolicy
import com.omnifind.app.ui.theme.OmniIcons
import com.omnifind.app.ui.theme.SmallAction
import com.omnifind.app.index.IndexStore
import com.omnifind.app.ui.theme.CheckRow
import com.omnifind.app.ui.theme.OmniCard
import com.omnifind.app.ui.theme.OmniColors
import com.omnifind.app.ui.theme.OmniSpace
import com.omnifind.app.ui.theme.OmniType
import com.omnifind.app.ui.theme.PillButton
import com.omnifind.app.ui.theme.ProgressLine
import com.omnifind.app.ui.theme.RowsCard
import com.omnifind.app.ui.theme.ScreenTitle
import com.omnifind.app.ui.theme.SectionLabel
import com.omnifind.app.ui.theme.Segmented
import java.text.SimpleDateFormat
import java.util.Date

/** What the index screen shows. */
data class IndexUi(
    val allowed: Boolean = true,
    val stats: IndexStore.Stats? = null,
    val running: String? = null,
    val lastRun: String? = null,
    val lastRunAt: Long? = null,
    val chosen: Set<IndexType> = emptySet(),
    val photoBudget: Int = 70,
    val screenshotBudget: Int = 140,
    val embedWhileCharging: Boolean = true,
    val threads: Int = 4,
    val volumes: List<VolumeUi> = emptyList(),
    val missing: List<String> = emptyList(),
    val caution: String? = null,
)

data class VolumeUi(
    val label: String, val removable: Boolean, val folders: List<FolderUi>,
    /** What the last Refresh found ("2 new folders: Scans, Tickets"). */
    val refreshNote: String? = null,
)
data class FolderUi(val name: String, val path: String, val wholeVolume: Boolean, val chosen: Boolean, val isNew: Boolean = false)

/** What the index screen can ask for. */
interface IndexActions {
    fun askPermission()
    fun indexNow()
    fun embedNow()
    fun stop()
    fun toggle(type: IndexType)
    fun toggleFolder(path: String)
    fun refreshVolume(label: String) {}
    fun choosePhotoBudget(budget: Int)
    fun chooseScreenshotBudget(budget: Int)
    fun chooseThreads(count: Int)
    fun changeEmbedWhileCharging(enabled: Boolean)
    fun confirmIndexing(dontAskAgain: Boolean)
    fun cancelIndexing()
}

@Composable
fun IndexScreen(model: IndexViewModel, onAskPermission: () -> Unit) {
    val state = IndexUi(
        allowed = model.allowed, stats = model.stats, running = model.running, lastRun = model.lastRun,
        lastRunAt = model.lastRunAt, chosen = IndexType.entries.filter(model::isChosen).toSet(),
        photoBudget = model.photoBudget, screenshotBudget = model.screenshotBudget,
        embedWhileCharging = model.embedWhileCharging, threads = model.threads,
        volumes = model.choices.map { (volume, choices) ->
            VolumeUi(volume.label, volume.removable, choices.map {
                FolderUi(it.folder.name, it.path, it.isWholeVolume, it.path in model.folders, it.path in model.newFolders)
            }, model.refreshNotes[volume.label])
        },
        missing = model.missing, caution = model.caution,
    )
    IndexContent(state, object : IndexActions {
        override fun askPermission() = onAskPermission()
        override fun indexNow() = model.indexNow()
        override fun embedNow() = model.embedNow()
        override fun stop() = model.stop()
        override fun toggle(type: IndexType) = model.toggle(type)
        override fun toggleFolder(path: String) = model.toggleFolder(path)
        override fun refreshVolume(label: String) = model.refreshVolume(label)
        override fun choosePhotoBudget(budget: Int) = model.choosePhotoBudget(budget)
        override fun chooseScreenshotBudget(budget: Int) = model.chooseScreenshotBudget(budget)
        override fun chooseThreads(count: Int) = model.chooseThreads(count)
        override fun changeEmbedWhileCharging(enabled: Boolean) = model.changeEmbedWhileCharging(enabled)
        override fun confirmIndexing(dontAskAgain: Boolean) = model.confirmIndexing(dontAskAgain)
        override fun cancelIndexing() = model.cancelIndexing()
    })
}

@Composable
fun IndexContent(state: IndexUi, actions: IndexActions) {
    state.caution?.let { CautionDialog(it, actions) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = OmniSpace.Margin),
    ) {
        Spacer(Modifier.height(19.dp))
        ScreenTitle("Index")
        Spacer(Modifier.height(14.5.dp))
        if (!state.allowed) {
            OmniCard {
                Text("OmniFind needs to read your files", style = OmniType.CardTitle)
                Spacer(Modifier.height(8.dp))
                Text("It only reads the folders you choose. Nothing leaves the phone.", style = OmniType.BodySmall)
                Spacer(Modifier.height(16.dp))
                PillButton("Allow access to files", actions::askPermission, Modifier.fillMaxWidth())
            }
            return@Column
        }

        Hero(state)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            PillButton(
                if (state.running != null) "Indexing…" else "Index now", actions::indexNow, Modifier.weight(1f),
                enabled = state.running == null && state.chosen.isNotEmpty(),
            )
            if (state.running != null) {
                PillButton("Stop", actions::stop, Modifier.weight(1f), filled = false)
            } else {
                PillButton("Embed on battery", actions::embedNow, Modifier.weight(1f), filled = false)
            }
        }
        Spacer(Modifier.height(15.dp))
        Text(
            "Index now reads new and changed files of the chosen types and skips ones already indexed. It then " +
                "embeds while charging, so it's best left overnight on the charger.",
            style = OmniType.BodySmall,
        )
        if (state.lastRun != null) {
            Spacer(Modifier.height(16.dp))
            OmniCard(padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                val locale = LocalConfiguration.current.locales[0]   // follows a change of language while open
                val at = state.lastRunAt?.let { SimpleDateFormat("d MMM, HH:mm", locale).format(Date(it)) }
                SectionLabel(listOfNotNull("Last run", at).joinToString(" · "))
                Spacer(Modifier.height(3.5.dp))
                Text(state.lastRun, style = OmniType.LastRun)
            }
        }

        Spacer(Modifier.height(17.5.dp))
        SectionLabel("File types to index")
        Spacer(Modifier.height(14.dp))
        RowsCard(rows = IndexType.entries.map { type ->
            { CheckRow(type.label, type.subtitle, type in state.chosen, onToggle = { actions.toggle(type) }) }
        })
        if (state.chosen.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Choose at least one type to index.", style = OmniType.BodySmall.copy(color = OmniColors.Error))
        }

        // The mockup ends with the file types; the rest of the settings start below the fold.
        Spacer(Modifier.height(48.dp))
        if (IndexType.PICTURES in state.chosen) {
            Spacer(Modifier.height(24.dp))
            SectionLabel("Picture detail")
            Spacer(Modifier.height(13.dp))
            OmniCard(padding = PaddingValues(16.dp)) {
                BudgetRow("Photos", listOf(70, 140), state.photoBudget, actions::choosePhotoBudget)
                Spacer(Modifier.height(12.dp))
                BudgetRow("Screenshots", listOf(140, 280), state.screenshotBudget, actions::chooseScreenshotBudget)
                Spacer(Modifier.height(12.dp))
                Text("More detail finds smaller things but takes longer. Changing it embeds those pictures again; " +
                    "text is never affected.", style = OmniType.RowSubtitle)
            }
        }

        Spacer(Modifier.height(24.dp))
        SectionLabel("Background")
        Spacer(Modifier.height(13.dp))
        OmniCard(padding = PaddingValues(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Embed whenever the phone charges", style = OmniType.RowTitle, modifier = Modifier.weight(1f))
                Switch(
                    checked = state.embedWhileCharging, onCheckedChange = actions::changeEmbedWhileCharging,
                    colors = SwitchDefaults.colors(checkedTrackColor = OmniColors.Primary, uncheckedTrackColor = OmniColors.Track),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text("CPU threads", style = OmniType.RowSubtitle)
            Spacer(Modifier.height(8.dp))
            Segmented(listOf(4, 6, 8), state.threads, { "$it" }, actions::chooseThreads, height = 44.dp)
        }

        Spacer(Modifier.height(24.dp))
        FoldersSection(state, actions)
        Spacer(Modifier.height(28.dp))
    }
}

/** The blue card: how many files are searchable, and how far meaning search has got. */
@Composable
private fun Hero(state: IndexUi) {
    val stats = state.stats
    OmniCard(color = OmniColors.Primary, padding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.5.dp, bottom = 18.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${stats?.done ?: 0}", style = OmniType.Count, modifier = Modifier.alignByBaseline())
            Spacer(Modifier.width(9.dp))
            Text("files searchable", style = OmniType.Body.copy(color = OmniColors.Surface, fontWeight = FontWeight(500)),
                modifier = Modifier.alignByBaseline())
        }
        Spacer(Modifier.height(5.dp))
        val done = (stats?.embedded ?: 0) + (stats?.picturesEmbedded ?: 0)
        val total = (stats?.pieces ?: 0) + (stats?.pictures ?: 0)
        val noun = when {
            stats == null -> "pieces"
            stats.pieces == 0 && stats.pictures > 0 -> "pictures"
            stats.pictures == 0 -> "text pieces"
            else -> "pieces"
        }
        Text(state.running ?: "$done of $total $noun searchable by meaning", style = OmniType.Caption)
        Spacer(Modifier.height(10.dp))
        ProgressLine(if (total == 0) 0f else done.toFloat() / total)
        val left = stats?.let { hoursLeft(it, state) } ?: 0.0
        if (left > 0.05) {
            Spacer(Modifier.height(10.dp))
            Text("About %.1f hours of embedding left".format(LocalConfiguration.current.locales[0], left), style = OmniType.Caption)
        }
    }
}

/** Seconds per text piece and per picture by vision budget, measured on the Mi A1 at 4 threads (2026-10-18). */
private const val SECONDS_PER_PIECE = 25
private val SECONDS_PER_PICTURE = mapOf(70 to 18, 140 to 40, 280 to 100)

private fun hoursLeft(stats: IndexStore.Stats, state: IndexUi): Double =
    (stats.waiting * SECONDS_PER_PIECE +
        (stats.picturesWaiting[FileKind.IMAGE] ?: 0) * (SECONDS_PER_PICTURE[state.photoBudget] ?: 18) +
        (stats.picturesWaiting[FileKind.SCREENSHOT] ?: 0) * (SECONDS_PER_PICTURE[state.screenshotBudget] ?: 40)) / 3600.0

@Composable
private fun BudgetRow(label: String, options: List<Int>, selected: Int, choose: (Int) -> Unit) {
    Column {
        Text(label, style = OmniType.RowTitle)
        Spacer(Modifier.height(8.dp))
        Segmented(options, selected, { if (it == options.first()) "Standard" else "Detailed" }, choose, height = 40.dp)
    }
}

@Composable
private fun CautionDialog(kinds: String, actions: IndexActions) {
    var dontAsk by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = actions::cancelIndexing,
        containerColor = OmniColors.Surface,
        title = { Text("Already indexed", style = OmniType.CardTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("You've chosen types that are already in the index: $kinds.", style = OmniType.Body)
                Text("They'll be skipped unless they changed since. Only new or changed files are read, and nothing " +
                    "that already has its vector is embedded again.", style = OmniType.BodySmall)
                CheckRow("Don't ask again", null, dontAsk, onToggle = { dontAsk = !dontAsk }, minHeight = 44.dp)
            }
        },
        confirmButton = { TextButton(onClick = { actions.confirmIndexing(dontAsk) }) { Text("Index", style = OmniType.ButtonSmall) } },
        dismissButton = { TextButton(onClick = actions::cancelIndexing) {
            Text("Cancel", style = OmniType.ButtonSmall.copy(color = OmniColors.Muted))
        } },
    )
}

/** "Folders to search": each storage with its Refresh, its folders (new ones marked), and what's protected. */
@Composable
fun FoldersSection(state: IndexUi, actions: IndexActions) {
    Column {
        SectionLabel("Folders to search")
        for (volume in state.volumes) {
            Spacer(Modifier.height(9.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(volume.label, style = OmniType.RowTitle.copy(color = OmniColors.Muted), modifier = Modifier.weight(1f))
                SmallAction("Refresh", OmniIcons.Refresh, { actions.refreshVolume(volume.label) })
            }
            volume.refreshNote?.let {
                Text(it, style = OmniType.RowSubtitle.copy(color = OmniColors.Primary, fontWeight = FontWeight(600)))
                Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.height(4.dp))
            RowsCard(rows = volume.folders.map { folder ->
                {
                    CheckRow(
                        if (folder.wholeVolume) "Everything on the ${volume.label.let { if (it.startsWith("SD")) it else it.replaceFirstChar(Char::lowercase) }}" else folder.name,
                        null, folder.chosen, onToggle = { actions.toggleFolder(folder.path) }, minHeight = 52.dp,
                        badge = if (folder.isNew) "New" else null,
                    )
                }
            })
        }
        Spacer(Modifier.height(10.dp))
        Text(FolderPolicy.EXPLANATION, style = OmniType.RowSubtitle)
        if (state.volumes.none { it.removable }) {
            Spacer(Modifier.height(10.dp))
            Text("No SD card found. Insert one (set up as portable storage) and come back to this tab.",
                style = OmniType.RowSubtitle)
        }
        if (state.missing.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            RowsCard(rows = state.missing.map { path ->
                { CheckRow(path, "Not there now; its files stay searchable until it's back", true,
                    onToggle = { actions.toggleFolder(path) }) }
            })
        }
        Spacer(Modifier.height(10.dp))
        Text("Changes apply on the next “Index now”. Folders you untick leave the index; types you untick stay " +
            "searchable.", style = OmniType.RowSubtitle)
    }
}
