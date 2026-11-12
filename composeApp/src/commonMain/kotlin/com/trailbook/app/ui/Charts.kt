package com.trailbook.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.trailbook.core.stats.ElevationSample
import com.trailbook.core.stats.SpeedSample
import kotlin.math.max
import kotlin.math.min

@Composable
fun ElevationChart(
    samples: List<ElevationSample>,
    lineColor: Color = Color(0xFF2E6B34),
    modifier: Modifier = Modifier,
) {
    val valid = samples.mapNotNull { it.elevation }
    if (valid.isEmpty()) {
        Canvas(modifier.fillMaxWidth().height(140.dp)) {
            drawLine(Color.Gray, Offset(0f, size.height / 2), Offset(size.width, size.height / 2))
        }
        return
    }
    val minE = valid.min()
    val maxE = valid.max()
    val range = max(maxE - minE, 1.0)
    Canvas(modifier.fillMaxWidth().height(140.dp)) {
        val w = size.width
        val h = size.height
        val path = Path()
        var started = false
        for (sample in samples) {
            val elevation = sample.elevation ?: continue
            val x = if (samples.last().distanceMeters > 0) {
                (sample.distanceMeters / samples.last().distanceMeters * w).toFloat()
            } else 0f
            val y = (h - ((elevation - minE) / range * (h - 16f) + 8f)).toFloat()
            if (!started) {
                path.moveTo(x, y)
                started = true
            } else {
                path.lineTo(x, y)
            }
        }
        drawPath(path, lineColor, style = Stroke(width = 4f))
    }
}

@Composable
fun SpeedChart(
    samples: List<SpeedSample>,
    lineColor: Color = Color(0xFF1565C0),
    modifier: Modifier = Modifier,
) {
    val valid = samples.mapNotNull { it.speedMps }
    if (valid.isEmpty()) {
        Canvas(modifier.fillMaxWidth().height(120.dp)) {
            drawLine(Color.Gray, Offset(0f, size.height / 2), Offset(size.width, size.height / 2))
        }
        return
    }
    val minS = 0.0
    val maxS = max(valid.max(), 0.1)
    val t0 = samples.first().time
    val t1 = max(samples.last().time, t0 + 1)
    Canvas(modifier.fillMaxWidth().height(120.dp)) {
        val w = size.width
        val h = size.height
        val path = Path()
        var started = false
        for (sample in samples) {
            val speed = sample.speedMps ?: continue
            val x = ((sample.time - t0).toDouble() / (t1 - t0) * w).toFloat()
            val y = (h - (speed - minS) / maxS * (h - 16f) - 8f).toFloat()
            if (!started) {
                path.moveTo(x, y)
                started = true
            } else {
                path.lineTo(x, y)
            }
        }
        drawPath(path, lineColor, style = Stroke(width = 3f))
    }
}
