package com.omnifind.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.omnifind.app.release.Release
import com.omnifind.app.ui.theme.CardShape
import com.omnifind.app.ui.theme.OmniCard
import com.omnifind.app.ui.theme.OmniColors
import com.omnifind.app.ui.theme.OmniSpace
import com.omnifind.app.ui.theme.OmniType
import com.omnifind.app.ui.theme.PillButton
import com.omnifind.app.ui.theme.ScreenTitle
import com.omnifind.app.ui.theme.SectionLabel
import com.omnifind.app.ui.theme.Tag
import com.omnifind.app.ui.theme.Wordmark
import kotlinx.coroutines.launch

/** The tour as the app shows it: screenshots from the app's assets. */
@Composable
fun TourScreen(release: Release, onDone: () -> Unit) {
    val context = LocalContext.current
    val cache = remember { HashMap<String, ImageBitmap?>() }
    TourContent(release, { name -> cache.getOrPut(name) { Release.screenshot(context, name)?.asImageBitmap() } }, onDone)
}

/**
 * "Take a tour of the features": one page per numbered step of [release]'s tour (number, what's new or fixed, a short
 * overview and the screenshot the build's tests captured), then a page of this build's fixes and known issues.
 * Swipe or tap Next; Skip leaves at any point.
 */
@Composable
fun TourContent(release: Release, screenshot: (String) -> ImageBitmap?, onDone: () -> Unit) {
    val pages = release.tour.size + 1
    val pager = rememberPagerState { pages }
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().background(OmniColors.Background).windowInsetsPadding(WindowInsets.safeDrawing),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = MaxContentWidth).fillMaxWidth().padding(horizontal = OmniSpace.Margin)) {
            Spacer(Modifier.height(15.dp))
            Wordmark()
            Spacer(Modifier.height(19.dp))
            ScreenTitle("Take a tour of the features")
            Spacer(Modifier.height(4.dp))
            Text("What's new in ${release.product} ${release.label}", style = OmniType.Subtitle)
        }
        // Clipped, so a long page (the fixes) scrolls inside the pager instead of drawing under the dots.
        HorizontalPager(pager, Modifier.weight(1f).widthIn(max = MaxContentWidth).fillMaxWidth().clipToBounds()) { page ->
            if (page < release.tour.size) Step(release.tour[page], release.tour.size, screenshot)
            else Fixes(release)
        }
        Column(Modifier.widthIn(max = MaxContentWidth).fillMaxWidth().padding(horizontal = OmniSpace.Margin)) {
            Dots(pages, pager.currentPage)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                val last = pager.currentPage == pages - 1
                if (!last) PillButton("Skip", onDone, Modifier.weight(1f), filled = false)
                PillButton(
                    if (last) "Start using ${release.product}" else "Next",
                    { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                    Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Step(step: Release.TourStep, count: Int, screenshot: (String) -> ImageBitmap?) {
    Column(Modifier.fillMaxSize().padding(horizontal = OmniSpace.Margin)) {
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(OmniColors.Primary), contentAlignment = Alignment.Center) {
                Text("${step.number}", style = OmniType.Button)
            }
            Spacer(Modifier.width(10.dp))
            Text("${step.number} of $count", style = OmniType.SectionLabel, modifier = Modifier.weight(1f))
            Tag(step.status)
        }
        Spacer(Modifier.height(12.dp))
        Text(step.title, style = OmniType.CardTitle.copy(fontSize = OmniType.Hero.fontSize * 0.86f))
        Spacer(Modifier.height(6.dp))
        Text(step.overview, style = OmniType.BodySmall.copy(color = OmniColors.Body))
        // What a real run showed: up to three highlights, ticked off in the brand green.
        for (highlight in step.highlights.take(3)) {
            Spacer(Modifier.height(5.dp))
            Row {
                Box(Modifier.padding(top = 6.dp).size(7.dp).clip(CircleShape).background(OmniColors.Accent))
                Spacer(Modifier.width(9.dp))
                Text(highlight, style = OmniType.RowSubtitle.copy(color = OmniColors.Ink, fontWeight = FontWeight(500)))
            }
        }
        Spacer(Modifier.height(12.dp))
        val image = screenshot(step.screenshot)
        Box(
            Modifier.weight(1f).fillMaxWidth().clip(CardShape).background(OmniColors.Surface)
                .border(1.dp, OmniColors.Divider, CardShape).padding(10.dp)
                .semantics { contentDescription = "Screenshot ${step.number}: ${step.title}" },
            contentAlignment = Alignment.Center,
        ) {
            if (image != null) {
                Image(image, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()
                    .clip(RoundedCornerShape(12.dp)))
            } else {
                Text("Screenshot ${step.screenshot}", style = OmniType.RowSubtitle, textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(14.dp))
    }
}

/** The last page: what this build fixes and what's still open. */
@Composable
private fun Fixes(release: Release) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = OmniSpace.Margin)) {
        Spacer(Modifier.height(18.dp))
        Text("Fixed and improved", style = OmniType.CardTitle.copy(fontSize = OmniType.Hero.fontSize * 0.86f))
        Spacer(Modifier.height(12.dp))
        OmniCard(padding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            release.fixes.forEachIndexed { index, fix ->
                if (index > 0) Spacer(Modifier.height(10.dp))
                Row {
                    Text("${index + 1}.", style = OmniType.RowTitle.copy(color = OmniColors.Primary), modifier = Modifier.width(26.dp))
                    Column(Modifier.weight(1f)) {
                        Text(fix.title, style = OmniType.BodySmall.copy(color = OmniColors.Ink))
                        Text(fix.build, style = OmniType.RowSubtitle)
                    }
                }
            }
        }
        if (release.known.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            SectionLabel("Known issues")
            Spacer(Modifier.height(10.dp))
            for (issue in release.known) {
                Text("• $issue", style = OmniType.BodySmall)
                Spacer(Modifier.height(6.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("${release.channel} · ${release.date} · ${release.minAndroid} to ${release.targetAndroid}",
            style = OmniType.RowSubtitle.copy(fontWeight = FontWeight(500)))
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Dots(count: Int, current: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        repeat(count) { index ->
            Box(
                Modifier.padding(horizontal = 4.dp).size(if (index == current) 22.dp else 8.dp, 8.dp).clip(CircleShape)
                    .background(if (index == current) OmniColors.Primary else OmniColors.Track),
            )
        }
    }
}
