package com.omnifind.app.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The OmniFind mark, in the proportions measured from the icon sheet (fractions of the icon's side): a white lens
 * (centre 0.452, outer radius 0.205, stroke 0.057), a handle at 45° to 0.705 (stroke 0.064, round caps), a green
 * four-point sparkle in the lens (radius 0.09) and a small dot above it to the right.
 */
fun DrawScope.drawMark(withTile: Boolean, cornerFraction: Float = 0.21f) {
    val s = size.minDimension
    if (withTile) {
        drawRoundRect(OmniColors.Primary, cornerRadius = CornerRadius(s * cornerFraction))
    }
    val c = Offset(0.4523f * s, 0.4523f * s)
    drawCircle(OmniColors.Surface, radius = 0.1761f * s, center = c, style = Stroke(width = 0.0568f * s))
    val start = 0.1761f * s * 0.7071f
    val end = 0.3634f * s * 0.7071f
    drawLine(
        OmniColors.Surface, Offset(c.x + start, c.y + start), Offset(c.x + end, c.y + end),
        strokeWidth = 0.0643f * s, cap = StrokeCap.Round,
    )
    val sc = Offset(0.4534f * s, 0.4534f * s)
    val r = 0.0898f * s
    val k = 0.16f * r   // how far the sides bow in from the centre: a full, rounded sparkle
    val star = Path().apply {
        moveTo(sc.x, sc.y - r)
        cubicTo(sc.x + k, sc.y - k, sc.x + k, sc.y - k, sc.x + r, sc.y)
        cubicTo(sc.x + k, sc.y + k, sc.x + k, sc.y + k, sc.x, sc.y + r)
        cubicTo(sc.x - k, sc.y + k, sc.x - k, sc.y + k, sc.x - r, sc.y)
        cubicTo(sc.x - k, sc.y - k, sc.x - k, sc.y - k, sc.x, sc.y - r)
        close()
    }
    drawPath(star, OmniColors.Accent)
    drawCircle(OmniColors.Accent, radius = 0.0148f * s, center = Offset(0.5261f * s, 0.3807f * s))
}

/** The app icon as a tile: blue rounded square with the mark. */
@Composable
fun LogoTile(size: Dp = 34.dp) {
    Canvas(Modifier.size(size)) { drawMark(withTile = true) }
}

/** The header lockup: tile and "OmniFind", "Omni" heavier than "Find", as on the icon sheet. */
@Composable
fun Wordmark() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LogoTile()
        Spacer(Modifier.width(9.5.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight(700))) { append("Omni") }
                withStyle(SpanStyle(fontWeight = FontWeight(500))) { append("Find") }
            },
            style = OmniType.Wordmark,
        )
    }
}
