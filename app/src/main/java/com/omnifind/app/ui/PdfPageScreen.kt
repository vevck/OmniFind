package com.omnifind.app.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A PDF opened at the page a search found. Android's PDF apps don't reliably take a page number, so OmniFind shows the
 * page itself (Android's own PdfRenderer), with "Open in…" for the full app.
 */
@Composable
fun PdfPageScreen(file: File, startPage: Int, onClose: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onClose)
    val document = remember(file) {
        runCatching {
            PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
        }
    }
    val lock = remember { Mutex() }   // PdfRenderer draws one page at a time
    DisposableEffect(document) { onDispose { document.getOrNull()?.close() } }
    val renderer = document.getOrNull()
    var page by remember { mutableIntStateOf((startPage - 1).coerceAtLeast(0)) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    if (renderer != null) {
        page = page.coerceIn(0, renderer.pageCount - 1)
        LaunchedEffect(page) {
            bitmap = null
            bitmap = withContext(Dispatchers.IO) {
                lock.withLock { render(renderer, page, context.resources.displayMetrics.widthPixels) }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("Back") }
            Text(file.name, style = MaterialTheme.typography.titleSmall, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            TextButton(onClick = { Files.open(context, file) }) { Text("Open in…") }
        }
        if (renderer == null) {
            Text("Can't show this PDF: ${document.exceptionOrNull()?.message}", Modifier.padding(16.dp))
            return@Column
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { page-- }, enabled = page > 0) { Text("Previous") }
            Text("Page ${page + 1} of ${renderer.pageCount}")
            OutlinedButton(onClick = { page++ }, enabled = page < renderer.pageCount - 1) { Text("Next") }
        }
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            val shown = bitmap
            if (shown == null) {
                CircularProgressIndicator(Modifier.padding(32.dp))
            } else {
                Image(shown.asImageBitmap(), contentDescription = "Page ${page + 1}",
                    contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** One page, as wide as the screen (twice as sharp, capped so a poster-sized page can't exhaust memory). */
private fun render(renderer: PdfRenderer, index: Int, screenWidth: Int): Bitmap = renderer.openPage(index).use { page ->
    val width = minOf(screenWidth * 2, 2400)
    val height = (width.toDouble() * page.height / page.width).toInt().coerceIn(1, 4800)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(Color.WHITE)
    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
    bitmap
}
