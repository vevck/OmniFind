package com.omnifind.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * OmniFind's icons, drawn after the mockups: outlined, 2-unit strokes on a 24-unit grid, round caps and joins.
 * Tinted where they're used.
 */
object OmniIcons {
    private fun icon(
        name: String, vararg strokes: PathBuilder.() -> Unit, fills: List<PathBuilder.() -> Unit> = emptyList(),
        weight: Float = 2f,
    ) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            for (stroke in strokes) {
                path(
                    fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = weight,
                    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = stroke,
                )
            }
            for (fill in fills) path(fill = SolidColor(Color.Black), pathBuilder = fill)
        }.build()

    private fun PathBuilder.roundRect(x: Float, y: Float, w: Float, h: Float, r: Float) {
        moveTo(x + r, y); lineTo(x + w - r, y); arcTo(r, r, 0f, false, true, x + w, y + r)
        lineTo(x + w, y + h - r); arcTo(r, r, 0f, false, true, x + w - r, y + h)
        lineTo(x + r, y + h); arcTo(r, r, 0f, false, true, x, y + h - r)
        lineTo(x, y + r); arcTo(r, r, 0f, false, true, x + r, y); close()
    }

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy); arcTo(r, r, 0f, true, true, cx + r, cy); arcTo(r, r, 0f, true, true, cx - r, cy); close()
    }

    val Search = icon("search", { circle(10.5f, 10.5f, 6.5f) }, { moveTo(15.4f, 15.4f); lineTo(20f, 20f) })

    val Folder = icon("folder", {
        moveTo(3f, 6.5f); arcTo(1.5f, 1.5f, 0f, false, true, 4.5f, 5f); lineTo(9.2f, 5f); lineTo(11.2f, 7f)
        lineTo(19.5f, 7f); arcTo(1.5f, 1.5f, 0f, false, true, 21f, 8.5f); lineTo(21f, 17.5f)
        arcTo(1.5f, 1.5f, 0f, false, true, 19.5f, 19f); lineTo(4.5f, 19f); arcTo(1.5f, 1.5f, 0f, false, true, 3f, 17.5f)
        close()
    })

    val Chip = icon(
        "chip",
        { roundRect(6f, 6f, 12f, 12f, 1f) },
        { roundRect(9.5f, 9.5f, 5f, 5f, 0.5f) },
        {
            for (x in listOf(9.5f, 14.5f)) { moveTo(x, 3f); lineTo(x, 6f); moveTo(x, 18f); lineTo(x, 21f) }
            for (y in listOf(9.5f, 14.5f)) { moveTo(3f, y); lineTo(6f, y); moveTo(18f, y); lineTo(21f, y) }
        },
    )

    /** A document: a page with a folded corner. */
    val File = icon(
        "file",
        {
            moveTo(6.5f, 3f); lineTo(14f, 3f); lineTo(19f, 8f); lineTo(19f, 19.5f)
            arcTo(1.5f, 1.5f, 0f, false, true, 17.5f, 21f); lineTo(6.5f, 21f)
            arcTo(1.5f, 1.5f, 0f, false, true, 5f, 19.5f); lineTo(5f, 4.5f)
            arcTo(1.5f, 1.5f, 0f, false, true, 6.5f, 3f); close()
        },
        fills = listOf({ moveTo(14f, 3f); lineTo(19f, 8f); lineTo(14f, 8f); close() }),
    )

    val Image = icon(
        "image",
        { roundRect(3f, 5f, 18f, 14f, 2f) },
        { moveTo(4f, 18f); lineTo(10f, 12.5f); lineTo(14f, 16f); lineTo(16f, 14f); lineTo(20f, 17.5f) },
        { circle(8f, 9.5f, 1.4f) },
    )

    val Phone = icon("phone", { roundRect(6f, 2.5f, 12f, 19f, 2.5f) }, { moveTo(10.25f, 18f); lineTo(13.75f, 18f) })

    val Receipt = icon(
        "receipt",
        {
            moveTo(6f, 3f); lineTo(18f, 3f); lineTo(18f, 20.5f); lineTo(16.5f, 19.4f); lineTo(15f, 20.5f)
            lineTo(13.5f, 19.4f); lineTo(12f, 20.5f); lineTo(10.5f, 19.4f); lineTo(9f, 20.5f); lineTo(7.5f, 19.4f)
            lineTo(6f, 20.5f); close()
        },
        { moveTo(9f, 8f); lineTo(15f, 8f); moveTo(9f, 12f); lineTo(15f, 12f) },
    )

    val Lock = icon("lock", { roundRect(5f, 11f, 14f, 10f, 2f) }, {
        moveTo(8f, 11f); lineTo(8f, 8f); arcTo(4f, 4f, 0f, false, true, 16f, 8f); lineTo(16f, 11f)
    })

    /** The checkbox tick: heavier than the other icons, as in the mockup. */
    val Check = icon("check", { moveTo(5.5f, 12.5f); lineTo(10f, 17f); lineTo(18.5f, 7.5f) }, weight = 3.2f)

    /** Refresh: a circular arrow. */
    val Refresh = icon("refresh", {
        moveTo(19f, 12f); arcTo(7f, 7f, 0f, true, true, 16.95f, 7.05f)
    }, { moveTo(17.5f, 3.5f); lineTo(17.5f, 7.5f); lineTo(13.5f, 7.5f) })

    val Back = icon("back", { moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f) })
}
