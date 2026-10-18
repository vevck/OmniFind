package com.omnifind.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.omnifind.app.R

/** OmniFind's colours, sampled from the design mockups. */
object OmniColors {
    val Primary = Color(0xFF2A44D4)        // brand blue: buttons, selection, the logo
    val Accent = Color(0xFF3DDC97)         // brand green: the sparkle, progress, "ready"
    val AccentHalo = Color(0xFFD6F7E8)
    val Background = Color(0xFFF3F5F7)
    val Surface = Color(0xFFFFFFFF)
    val Ink = Color(0xFF0F1B2D)            // titles
    val Body = Color(0xFF2D3748)           // running text
    val Muted = Color(0xFF4A5568)          // labels, secondary text, unselected tabs
    val Placeholder = Color(0xFF6B7385)
    val Border = Color(0xFFD5DBE3)         // outlined chips and fields
    val Divider = Color(0xFFEDF0F4)        // between rows in a card
    val Track = Color(0xFFE3E7EE)          // segmented control, the tab bar's top line
    val CheckboxBorder = Color(0xFF767676)
    val PrimarySoft = Color(0xFFE6EAFC)    // tags, the privacy note
    val TabIndicator = Color(0xFFE3E8FF)
    val PrimaryInk = Color(0xFF1F33A8)     // text on soft blue
    val OnPrimarySoft = Color(0xFFDCE2FF)  // secondary text on brand blue
    val Error = Color(0xFFC53030)
}

/**
 * Sora (SIL Open Font License, third_party/Sora-OFL.txt), the mockups' typeface: one variable font, any weight.
 * Measured against the mockups (2026-10-18): titles 700, labels 600, body 400–500.
 */
@OptIn(ExperimentalTextApi::class)
private fun sora(weight: Int) =
    Font(R.font.sora, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val Sora = FontFamily(listOf(300, 400, 450, 500, 600, 700, 800).map(::sora))

/** The type scale, in the mockups' sizes (sp = the mockup's px / 2). */
object OmniType {
    private fun style(size: Float, weight: Int, color: Color, line: Float = 1.3f, tracking: Float = 0f) = TextStyle(
        fontFamily = Sora, fontSize = size.sp, fontWeight = FontWeight(weight), color = color,
        lineHeight = (size * line).sp, letterSpacing = tracking.em,
    )

    val Wordmark = style(20.2f, 700, OmniColors.Ink, line = 1.2f, tracking = -0.01f)
    val ScreenTitle = style(27.5f, 700, OmniColors.Ink, line = 1.2f, tracking = -0.01f)
    val Subtitle = style(14f, 400, OmniColors.Muted, line = 1.25f)
    val Hero = style(23.3f, 700, OmniColors.Ink, line = 1.41f, tracking = -0.003f)
    val HeroSoft = style(23.6f, 400, OmniColors.Muted, line = 1.35f, tracking = -0.01f)
    val Body = style(15f, 400, OmniColors.Body, line = 1.53f)
    val BodySmall = style(12.85f, 400, OmniColors.Muted, line = 1.575f)
    val SectionLabel = style(11.8f, 650, OmniColors.Muted, line = 1.2f, tracking = 0.1f)
    val RowTitle = style(15.25f, 600, OmniColors.Ink, line = 1.3f)
    val RowText = style(15f, 450, OmniColors.Ink, line = 1.3f)
    val RowSubtitle = style(12.25f, 400, OmniColors.Muted, line = 1.35f)
    val Chip = style(14f, 500, OmniColors.Ink, line = 1.2f)
    val Tag = style(13f, 600, OmniColors.PrimaryInk, line = 1.2f)
    val Button = style(15f, 700, OmniColors.Surface, line = 1.2f)
    val ButtonSmall = style(14f, 700, OmniColors.Primary, line = 1.2f)
    val CardTitle = style(18f, 700, OmniColors.Ink, line = 1.2f)
    val Field = style(15f, 400, OmniColors.Ink, line = 1.3f)
    val TabLabel = style(12f, 500, OmniColors.Muted, line = 1.2f)
    val Count = style(40f, 800, OmniColors.Surface, line = 1.1f, tracking = -0.02f)
    val Caption = style(13f, 500, OmniColors.OnPrimarySoft, line = 1.3f)
    val LastRun = style(14.1f, 400, OmniColors.Ink, line = 1.3f)
    val Note = style(12.8f, 500, OmniColors.PrimaryInk, line = 1.45f)
}

/** Always the light design: the mockups define no dark one. */
@Composable
fun OmniTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = OmniColors.Primary, onPrimary = OmniColors.Surface,
            secondary = OmniColors.Accent, background = OmniColors.Background, surface = OmniColors.Surface,
            onBackground = OmniColors.Ink, onSurface = OmniColors.Ink, onSurfaceVariant = OmniColors.Muted,
            surfaceVariant = OmniColors.Track, outline = OmniColors.Border, error = OmniColors.Error,
            primaryContainer = OmniColors.PrimarySoft, onPrimaryContainer = OmniColors.PrimaryInk,
            surfaceContainerHigh = OmniColors.Surface,
        ),
        typography = MaterialTheme.typography.run {
            copy(
                bodyLarge = OmniType.Body, bodyMedium = OmniType.BodySmall, bodySmall = OmniType.RowSubtitle,
                titleLarge = OmniType.CardTitle, titleMedium = OmniType.RowTitle, labelLarge = OmniType.ButtonSmall,
                headlineSmall = OmniType.CardTitle,
            )
        },
        content = content,
    )
}
