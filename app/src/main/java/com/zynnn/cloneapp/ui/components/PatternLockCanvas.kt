package com.zynnn.cloneapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.zynnn.cloneapp.ui.theme.LocalLiquidGlass
import kotlin.math.hypot

@Composable
fun PatternLockCanvas(
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    onPatternComplete: (String) -> Unit
) {
    val glass = LocalLiquidGlass.current
    val haptic = LocalHapticFeedback.current
    val selectedPoints = remember { mutableStateListOf<Int>() }
    var dragPos by remember { mutableStateOf<Offset?>(null) }
    var dotCenters by remember { mutableStateOf<List<Offset>>(emptyList()) }

    val lineColor = if (isError) glass.accentRose else glass.accentCyan

    Canvas(
        modifier = modifier
            .size(260.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { start ->
                        selectedPoints.clear()
                        dragPos = start
                        dotCenters.forEachIndexed { idx, center ->
                            if (hypot(start.x - center.x, start.y - center.y) < 52f && idx !in selectedPoints) {
                                selectedPoints.add(idx)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val pos = change.position
                        dragPos = pos
                        dotCenters.forEachIndexed { idx, center ->
                            if (hypot(pos.x - center.x, pos.y - center.y) < 52f && idx !in selectedPoints) {
                                selectedPoints.add(idx)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    },
                    onDragEnd = {
                        dragPos = null
                        if (selectedPoints.isNotEmpty()) {
                            val code = selectedPoints.joinToString("-")
                            selectedPoints.clear()
                            onPatternComplete(code)
                        }
                    },
                    onDragCancel = {
                        dragPos = null
                        selectedPoints.clear()
                    }
                )
            }
    ) {
        val stepX = size.width / 3f
        val stepY = size.height / 3f
        val centers = buildList {
            for (r in 0..2) {
                for (c in 0..2) {
                    add(Offset(stepX * (c + 0.5f), stepY * (r + 0.5f)))
                }
            }
        }
        dotCenters = centers

        // Connector lines
        for (i in 0 until selectedPoints.size - 1) {
            val from = centers[selectedPoints[i]]
            val to = centers[selectedPoints[i + 1]]
            drawLine(
                brush = Brush.linearGradient(listOf(lineColor, glass.accentViolet)),
                start = from,
                end = to,
                strokeWidth = 10f,
                cap = StrokeCap.Round
            )
        }
        val activeDrag = dragPos
        if (selectedPoints.isNotEmpty() && activeDrag != null) {
            drawLine(
                color = lineColor.copy(alpha = 0.65f),
                start = centers[selectedPoints.last()],
                end = activeDrag,
                strokeWidth = 8f,
                cap = StrokeCap.Round
            )
        }

        // 9 Nodes
        centers.forEachIndexed { idx, center ->
            val isSelected = idx in selectedPoints
            drawCircle(
                color = if (isSelected) lineColor.copy(alpha = 0.25f) else glass.glassTint.copy(alpha = 0.14f),
                radius = if (isSelected) 34f else 26f,
                center = center
            )
            drawCircle(
                color = if (isSelected) lineColor else glass.borderStart,
                radius = if (isSelected) 34f else 26f,
                center = center,
                style = Stroke(width = 2.5f)
            )
            drawCircle(
                color = if (isSelected) Color.White else glass.textSecondary.copy(alpha = 0.7f),
                radius = if (isSelected) 11f else 7f,
                center = center
            )
        }
    }
}
