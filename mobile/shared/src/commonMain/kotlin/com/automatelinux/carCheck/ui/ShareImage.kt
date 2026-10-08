package com.automatelinux.carCheck.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt

/** Draw as usual, and keep what was drawn in [layer] so it can be turned into a picture later. */
fun Modifier.recordInto(layer: GraphicsLayer): Modifier = drawWithContent {
    layer.record { this@drawWithContent.drawContent() }
    drawLayer(layer)
}

/**
 * The report as one picture: the plate centred on top, then [parts] stacked in order, on [background].
 * Each part is a recorded slice of the screen, so the picture is exactly what the user sees — same
 * fonts, same theme, no second layout to drift from the real one.
 */
suspend fun stackShareImage(
    plate: GraphicsLayer,
    parts: List<GraphicsLayer>,
    background: Color,
    density: Density,
    paddingPx: Float,
): ImageBitmap {
    val plateBmp = plate.toImageBitmap()
    val partBmps = parts.map { it.toImageBitmap() }.filter { it.width > 0 && it.height > 0 }
    val contentWidth = (partBmps.maxOfOrNull { it.width } ?: 0).coerceAtLeast(plateBmp.width)
    val width = (contentWidth + 2 * paddingPx).roundToInt()
    val height = (paddingPx + plateBmp.height + partBmps.sumOf { it.height } + paddingPx).roundToInt()
    val out = ImageBitmap(width, height)
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(out), Size(width.toFloat(), height.toFloat())) {
        drawRect(background)
        drawImage(plateBmp, Offset((width - plateBmp.width) / 2f, paddingPx))
        var y = paddingPx + plateBmp.height
        for (b in partBmps) {
            drawImage(b, Offset(paddingPx, y))
            y += b.height
        }
    }
    return out
}
