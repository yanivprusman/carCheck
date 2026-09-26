package com.automatelinux.carCheck.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.automatelinux.carCheck.data.Plate
import com.automatelinux.carCheck.ui.theme.Plate as PlateColors

/** Real plates are 520×110 mm; a little squarer here so the digits keep their weight on a phone. */
private const val PLATE_ASPECT = 4.2f

/**
 * An Israeli registration plate, drawn — yellow field, blue band with the flag and
 * "IL", black digits. It is a physical object, so it is laid out left-to-right
 * whatever the UI direction: the band is on the left of a real plate.
 */
@Composable
fun PlateView(text: String, modifier: Modifier = Modifier, height: Dp = 56.dp) {
    PlateFrame(modifier = modifier, height = height) {
        PlateDigits(text = text, height = height, modifier = Modifier.fillMaxSize())
    }
}

/**
 * The same plate, but the digits are typed into it. Digits only, at most eight;
 * the dashes appear as the number grows, the way the printed plate groups them.
 */
@Composable
fun PlateInput(
    digits: String,
    onDigitsChange: (String) -> Unit,
    onSearch: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 84.dp,
) {
    val fontSize = with(LocalDensity.current) { (height * 0.52f).toSp() }
    PlateFrame(modifier = modifier, height = height) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (digits.isEmpty()) {
                Text(
                    "12-345-67",
                    style = digitStyle(fontSize),
                    color = PlateColors.InkOnYellow.copy(alpha = 0.18f),
                    maxLines = 1,
                    softWrap = false,
                )
            }
            BasicTextField(
                value = digits,
                onValueChange = { raw -> onDigitsChange(raw.filter { it.isDigit() }.take(Plate.MAX_DIGITS)) },
                enabled = enabled,
                singleLine = true,
                textStyle = digitStyle(fontSize).copy(textAlign = TextAlign.Center),
                cursorBrush = SolidColor(PlateColors.InkOnYellow),
                visualTransformation = PlateTransformation,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { inner() }
                },
            )
        }
    }
}

@Composable
private fun PlateFrame(modifier: Modifier, height: Dp, content: @Composable () -> Unit) {
    val radius = height * 0.11f
    val frame = (height * 0.035f).coerceAtLeast(1.dp)
    val shape = RoundedCornerShape(radius)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier
                .height(height)
                .aspectRatio(PLATE_ASPECT)
                .clip(shape)
                .background(PlateColors.Yellow)
                .border(frame, PlateColors.Ink, shape),
        ) {
            BlueBand(height = height)
            Box(Modifier.fillMaxHeight().weight(1f).padding(vertical = frame)) { content() }
        }
    }
}

@Composable
private fun BlueBand(height: Dp) {
    val bandWidth = height * 0.34f
    Column(
        modifier = Modifier.fillMaxHeight().width(bandWidth).background(PlateColors.Blue).padding(vertical = height * 0.09f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        if (height >= 40.dp) {
            Flag(Modifier.size(width = bandWidth * 0.62f, height = bandWidth * 0.46f))
            Text(
                "IL",
                color = Color.White,
                fontSize = with(LocalDensity.current) { (height * 0.2f).toSp() },
                fontWeight = FontWeight.Bold,
                lineHeight = with(LocalDensity.current) { (height * 0.2f).toSp() },
                letterSpacing = 0.sp,
            )
        } else {
            Box(Modifier.size(width = bandWidth * 0.55f, height = height * 0.12f).background(Color.White, RoundedCornerShape(1.dp)))
            Box(Modifier.size(width = bandWidth * 0.55f, height = height * 0.12f).background(Color.White, RoundedCornerShape(1.dp)))
        }
    }
}

/** The flag on the band: two blue stripes and a Star of David on a white field, as small as it can still be read. */
@Composable
private fun Flag(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(Color.White)
        val stripe = h * 0.16f
        drawRect(PlateColors.Blue, topLeft = Offset(0f, h * 0.12f), size = androidx.compose.ui.geometry.Size(w, stripe))
        drawRect(PlateColors.Blue, topLeft = Offset(0f, h * 0.72f), size = androidx.compose.ui.geometry.Size(w, stripe))
        val cx = w / 2
        val cy = h / 2
        val r = h * 0.22f
        val sw = (h * 0.07f).coerceAtLeast(0.8f)
        val up = Path().apply {
            moveTo(cx, cy - r); lineTo(cx + r * 0.866f, cy + r * 0.5f); lineTo(cx - r * 0.866f, cy + r * 0.5f); close()
        }
        val down = Path().apply {
            moveTo(cx, cy + r); lineTo(cx + r * 0.866f, cy - r * 0.5f); lineTo(cx - r * 0.866f, cy - r * 0.5f); close()
        }
        drawPath(up, PlateColors.Blue, style = Stroke(sw))
        drawPath(down, PlateColors.Blue, style = Stroke(sw))
    }
}

@Composable
private fun PlateDigits(text: String, height: Dp, modifier: Modifier) {
    val fontSize = with(LocalDensity.current) { (height * 0.52f).toSp() }
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            text,
            style = digitStyle(fontSize),
            color = PlateColors.InkOnYellow,
            maxLines = 1,
            softWrap = false,
        )
    }
}

private fun digitStyle(fontSize: androidx.compose.ui.unit.TextUnit) = TextStyle(
    fontSize = fontSize,
    lineHeight = fontSize,
    fontWeight = FontWeight.Black,
    letterSpacing = fontSize * 0.02f,
    fontFeatureSettings = "tnum",
)

/** Inserts the plate's dashes into typed digits; insert-only, so the offset maps are arithmetic. */
private object PlateTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val out = Plate.format(digits)
        val boundaries = boundaries(digits.length)
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                offset + boundaries.count { it <= offset }
            override fun transformedToOriginal(offset: Int): Int {
                var dashes = 0
                for ((k, b) in boundaries.withIndex()) if (b + k < offset) dashes++
                return (offset - dashes).coerceIn(0, digits.length)
            }
        }
        return TransformedText(AnnotatedString(out), mapping)
    }

    /** Digit indexes a dash is inserted before, for this length. */
    private fun boundaries(length: Int): List<Int> {
        val groups = Plate.groupsFor(length)
        val out = mutableListOf<Int>()
        var acc = 0
        for (i in 0 until groups.size - 1) {
            acc += groups[i]
            if (acc < length) out += acc
        }
        return out
    }
}
