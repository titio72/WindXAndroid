package com.aboni.n2kRouter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.aboni.marine.ui.theme.Marine
import com.aboni.marine.ui.theme.current
import com.aboni.n2kRouter.Calibration
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val DIAL_STEP = 2

/** A point at [radius] from [c], [deg] clockwise from the bow (up). */
private fun polar(c: Offset, radius: Float, deg: Float): Offset {
    val a = Math.toRadians(deg.toDouble())
    return Offset(c.x + radius * sin(a).toFloat(), c.y - radius * cos(a).toFloat())
}

/**
 * The wind dial: starboard and port sectors, a tick every 2°, labels every 30°, then the three needles (raw, smoothed,
 * output). The raw needle's inner line shows the ellipse error; [calibration] marks the angles covered so far.
 * A null angle hides its needle.
 */
@Composable
fun WindDial(
    raw: Double?,
    smooth: Double?,
    output: Double?,
    ellipse: Double?,
    calibration: Calibration?,
    modifier: Modifier = Modifier,
) {
    val dialColor = Marine.colors.label
    val labelStyle = Marine.type.label.copy(color = dialColor)
    val starboard = WindColors.starboard.current()
    val port = WindColors.port.current()
    val needle = WindColors.needle.current()
    val smoothNeedle = WindColors.smoothNeedle.current()
    val outputNeedle = WindColors.outputNeedle.current()
    val calibColor = WindColors.calibrating.current()
    val measurer = rememberTextMeasurer()
    Spacer(
        modifier
            .semantics { contentDescription = "Wind dial" }
            .drawWithCache {
                val c = Offset(size.width / 2f, size.height / 2f)
                val r = min(size.width, size.height) / 2f * 0.75f
                val thin = 1.5.dp.toPx()
                val ticks = Path().apply {
                    for (i in 0 until 360 step DIAL_STEP) {
                        val inner = if (i % 30 == 0) r * 0.9f else r
                        val a = polar(c, inner, i.toFloat())
                        val b = polar(c, r * 1.1f, i.toFloat())
                        moveTo(a.x, a.y)
                        lineTo(b.x, b.y)
                    }
                }
                val labels = (0 until 360 step 30).map { i ->
                    val t = measurer.measure((if (i > 180) 360 - i else i).toString(), labelStyle)
                    val p = polar(c, r * 1.25f, i.toFloat())
                    t to Offset(p.x - t.size.width / 2f, p.y - t.size.height / 2f)
                }
                val sectorR = r * 0.95f
                val sectorTopLeft = Offset(c.x - sectorR, c.y - sectorR)
                val sectorSize = Size(sectorR * 2, sectorR * 2)
                val sectorStroke = Stroke(4.dp.toPx())
                onDrawBehind {
                    // Cross hair, broken around the centre.
                    drawLine(dialColor, Offset(0f, c.y), Offset(c.x - r * 0.5f, c.y), thin)
                    drawLine(dialColor, Offset(c.x + r * 0.5f, c.y), Offset(size.width, c.y), thin)
                    drawLine(dialColor, Offset(c.x, 0f), Offset(c.x, c.y - r * 0.5f), thin)
                    drawLine(dialColor, Offset(c.x, c.y + r * 0.5f), Offset(c.x, size.height), thin)
                    // Arcs start at 3 o'clock and go clockwise: starboard is 30°..150° off the bow on the right.
                    drawArc(starboard, 300f, 120f, false, sectorTopLeft, sectorSize, style = sectorStroke)
                    drawArc(port, 120f, 120f, false, sectorTopLeft, sectorSize, style = sectorStroke)
                    drawPath(ticks, dialColor, style = Stroke(thin))
                    drawCircle(dialColor, r * 0.8f, c, style = Stroke(thin))
                    labels.forEach { (t, at) -> drawText(t, topLeft = at) }

                    calibration?.let { cal ->
                        for (i in 0 until 360 step DIAL_STEP) {
                            if (!cal.isAngleOk(i.toDouble())) continue
                            drawLine(calibColor, polar(c, r * 1.05f, i.toFloat()), polar(c, r * 1.08f, i.toFloat()), 4.dp.toPx())
                        }
                    }
                    raw?.let { drawNeedle(needle, it, r, ellipse, dialColor, thin) }
                    smooth?.let { drawNeedle(smoothNeedle, it, r, null, dialColor, thin) }
                    output?.let { drawNeedle(outputNeedle, it, r, null, dialColor, thin) }
                }
            }
    )
}

private fun DrawScope.drawNeedle(color: Color, angle: Double, r: Float, err: Double?, errColor: Color, thin: Float) {
    val c = center
    rotate(angle.toFloat(), c) {
        val p = Path().apply {
            moveTo(c.x, c.y - r)
            lineTo(c.x - r * 0.03f, c.y - r * 1.08f)
            lineTo(c.x + r * 0.03f, c.y - r * 1.08f)
            close()
        }
        drawPath(p, color, style = Stroke(6.dp.toPx()))
        if (err != null && err > 0.0) {
            drawLine(errColor, Offset(c.x, c.y - r * 0.8f * err.toFloat()), Offset(c.x, c.y - r * 0.8f), thin)
        }
    }
}

/** An ADC reading on 0..[max] with its calibrated [low]..[high] range shaded. */
@Composable
fun RangeBar(value: Int?, low: Int?, high: Int?, max: Int, modifier: Modifier = Modifier) {
    val frame = Marine.colors.label
    val range = Marine.colors.primary.copy(alpha = 0.45f)
    val marker = WindColors.needle.current()
    Canvas(modifier) {
        val w = size.width
        if (low != null && high != null) {
            drawRect(range, Offset(w * low / max, 0f), Size(w * (high - low).coerceAtLeast(0) / max, size.height))
        }
        drawRect(frame, style = Stroke(1.5.dp.toPx()))
        value?.let {
            val x = w * it / max
            drawLine(marker, Offset(x, 0f), Offset(x, size.height), 4.dp.toPx())
        }
    }
}

/**
 * Calibration coverage over 0..360° from left to right, and the current [angle]. Covered angles turn to the
 * "calibrated" colour once the score is reached ([inScore]).
 */
@Composable
fun CalibrationStrip(calibration: Calibration?, angle: Double?, inScore: Boolean, modifier: Modifier = Modifier) {
    val frame = Marine.colors.label
    val covered = if (inScore) WindColors.calibrated.current() else WindColors.calibrating.current()
    val marker = WindColors.needle.current()
    Canvas(modifier) {
        val w = size.width - 4.dp.toPx()
        val x0 = 2.dp.toPx()
        val inset = size.height * 0.2f
        drawRect(frame, style = Stroke(1.5.dp.toPx()))
        calibration?.let { cal ->
            for (i in 0 until 360 step DIAL_STEP) {
                if (!cal.isAngleOk(i.toDouble())) continue
                val x = x0 + w * i / 360f
                drawLine(covered, Offset(x, inset), Offset(x, size.height - inset), 3.dp.toPx())
            }
        }
        angle?.let {
            val x = x0 + w * (it.toFloat() / 360f)
            drawLine(marker, Offset(x, 0f), Offset(x, size.height), 3.dp.toPx())
        }
    }
}
