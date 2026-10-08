package com.automatelinux.carCheck.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
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
 *
 * The slices are stacked as layers inside [into] and rasterised once. Rasterising each slice and
 * drawing the bitmaps onto a fresh canvas does not work on Android: toImageBitmap() returns
 * hardware bitmaps, and a software canvas refuses them.
 */
suspend fun stackShareImage(
    into: GraphicsLayer,
    plate: GraphicsLayer,
    parts: List<GraphicsLayer>,
    background: Color,
    density: Density,
    paddingPx: Float,
): ImageBitmap {
    val shown = parts.filter { it.size.width > 0 && it.size.height > 0 }
    val contentWidth = (shown.maxOfOrNull { it.size.width } ?: 0).coerceAtLeast(plate.size.width)
    val width = (contentWidth + 2 * paddingPx).roundToInt()
    val height = (paddingPx + plate.size.height + shown.sumOf { it.size.height } + paddingPx).roundToInt()
    into.record(density, LayoutDirection.Ltr, IntSize(width, height)) {
        drawRect(background)
        var y = paddingPx
        translate((width - plate.size.width) / 2f, y) { drawLayer(plate) }
        y += plate.size.height
        for (layer in shown) {
            translate(paddingPx, y) { drawLayer(layer) }
            y += layer.size.height
        }
    }
    return into.toImageBitmap()
}
