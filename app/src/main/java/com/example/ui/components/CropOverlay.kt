package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.detection.CropRect
import com.example.ui.theme.BorderFocus
import com.example.ui.theme.ElectricCyan
import kotlin.math.abs

private enum class DragHandle {
    NONE, CENTER, LEFT, RIGHT, TOP, BOTTOM, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
}

@Composable
fun CropOverlay(
    cropRect: CropRect,
    onCropRectChange: (CropRect) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeHandle by remember { mutableStateOf(DragHandle.NONE) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(cropRect) {
                detectDragGestures(
                    onDragStart = { touchOffset ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()

                        val left = cropRect.left * w
                        val top = cropRect.top * h
                        val right = cropRect.right * w
                        val bottom = cropRect.bottom * h

                        val touchRadius = 48f // raio tátil generoso

                        // Detecta cantos primeiro
                        activeHandle = when {
                            abs(touchOffset.x - left) <= touchRadius && abs(touchOffset.y - top) <= touchRadius -> DragHandle.TOP_LEFT
                            abs(touchOffset.x - right) <= touchRadius && abs(touchOffset.y - top) <= touchRadius -> DragHandle.TOP_RIGHT
                            abs(touchOffset.x - left) <= touchRadius && abs(touchOffset.y - bottom) <= touchRadius -> DragHandle.BOTTOM_LEFT
                            abs(touchOffset.x - right) <= touchRadius && abs(touchOffset.y - bottom) <= touchRadius -> DragHandle.BOTTOM_RIGHT

                            // Detecta bordas
                            abs(touchOffset.x - left) <= touchRadius && touchOffset.y in top..bottom -> DragHandle.LEFT
                            abs(touchOffset.x - right) <= touchRadius && touchOffset.y in top..bottom -> DragHandle.RIGHT
                            abs(touchOffset.y - top) <= touchRadius && touchOffset.x in left..right -> DragHandle.TOP
                            abs(touchOffset.y - bottom) <= touchRadius && touchOffset.x in left..right -> DragHandle.BOTTOM

                            // Centro (mover o retângulo inteiro)
                            touchOffset.x in left..right && touchOffset.y in top..bottom -> DragHandle.CENTER
                            else -> DragHandle.NONE
                        }
                    },
                    onDragEnd = { activeHandle = DragHandle.NONE },
                    onDragCancel = { activeHandle = DragHandle.NONE },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        if (w <= 0 || h <= 0) return@detectDragGestures

                        val dx = dragAmount.x / w
                        val dy = dragAmount.y / h

                        var newLeft = cropRect.left
                        var newTop = cropRect.top
                        var newRight = cropRect.right
                        var newBottom = cropRect.bottom

                        val minSize = 0.15f // tamanho mínimo de corte (15% da dimensão)

                        when (activeHandle) {
                            DragHandle.CENTER -> {
                                val boxW = newRight - newLeft
                                val boxH = newBottom - newTop
                                newLeft = (newLeft + dx).coerceIn(0f, 1f - boxW)
                                newRight = newLeft + boxW
                                newTop = (newTop + dy).coerceIn(0f, 1f - boxH)
                                newBottom = newTop + boxH
                            }
                            DragHandle.LEFT -> {
                                newLeft = (newLeft + dx).coerceIn(0f, newRight - minSize)
                            }
                            DragHandle.RIGHT -> {
                                newRight = (newRight + dx).coerceIn(newLeft + minSize, 1f)
                            }
                            DragHandle.TOP -> {
                                newTop = (newTop + dy).coerceIn(0f, newBottom - minSize)
                            }
                            DragHandle.BOTTOM -> {
                                newBottom = (newBottom + dy).coerceIn(newTop + minSize, 1f)
                            }
                            DragHandle.TOP_LEFT -> {
                                newLeft = (newLeft + dx).coerceIn(0f, newRight - minSize)
                                newTop = (newTop + dy).coerceIn(0f, newBottom - minSize)
                            }
                            DragHandle.TOP_RIGHT -> {
                                newRight = (newRight + dx).coerceIn(newLeft + minSize, 1f)
                                newTop = (newTop + dy).coerceIn(0f, newBottom - minSize)
                            }
                            DragHandle.BOTTOM_LEFT -> {
                                newLeft = (newLeft + dx).coerceIn(0f, newRight - minSize)
                                newBottom = (newBottom + dy).coerceIn(newTop + minSize, 1f)
                            }
                            DragHandle.BOTTOM_RIGHT -> {
                                newRight = (newRight + dx).coerceIn(newLeft + minSize, 1f)
                                newBottom = (newBottom + dy).coerceIn(newTop + minSize, 1f)
                            }
                            DragHandle.NONE -> {}
                        }

                        if (newRight > newLeft && newBottom > newTop) {
                            onCropRectChange(CropRect(newLeft, newTop, newRight, newBottom))
                        }
                    }
                )
            }
    ) {
        val w = size.width
        val h = size.height

        val left = cropRect.left * w
        val top = cropRect.top * h
        val right = cropRect.right * w
        val bottom = cropRect.bottom * h
        val rectW = right - left
        val rectH = bottom - top

        val scrimColor = Color.Black.copy(alpha = 0.65f)

        // 1. Escurecimento das áreas descartadas (fora do corte)
        // Top
        drawRect(color = scrimColor, topLeft = Offset.Zero, size = Size(w, top))
        // Bottom
        drawRect(color = scrimColor, topLeft = Offset(0f, bottom), size = Size(w, h - bottom))
        // Left
        drawRect(color = scrimColor, topLeft = Offset(0f, top), size = Size(left, rectH))
        // Right
        drawRect(color = scrimColor, topLeft = Offset(right, top), size = Size(w - right, rectH))

        // 2. Linhas de Guia (Regra dos terços)
        val oneThirdW = rectW / 3f
        val oneThirdH = rectH / 3f
        val gridColor = ElectricCyan.copy(alpha = 0.35f)
        val strokeGuide = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))

        // Verticais internas
        drawLine(gridColor, Offset(left + oneThirdW, top), Offset(left + oneThirdW, bottom), strokeGuide.width, pathEffect = strokeGuide.pathEffect)
        drawLine(gridColor, Offset(left + 2 * oneThirdW, top), Offset(left + 2 * oneThirdW, bottom), strokeGuide.width, pathEffect = strokeGuide.pathEffect)
        // Horizontais internas
        drawLine(gridColor, Offset(left, top + oneThirdH), Offset(right, top + oneThirdH), strokeGuide.width, pathEffect = strokeGuide.pathEffect)
        drawLine(gridColor, Offset(left, top + 2 * oneThirdH), Offset(right, top + 2 * oneThirdH), strokeGuide.width, pathEffect = strokeGuide.pathEffect)

        // 3. Moldura de Foco Ativa
        val frameStroke = Stroke(width = 3.dp.toPx())
        drawRect(
            color = ElectricCyan,
            topLeft = Offset(left, top),
            size = Size(rectW, rectH),
            style = frameStroke
        )

        // 4. Cantos Táteis Reforçados (Âncoras em L de alta precisão)
        val cornerLen = 22.dp.toPx()
        val cornerStroke = Stroke(width = 5.dp.toPx())
        val cornerColor = Color.White

        // Top-Left
        drawLine(cornerColor, Offset(left, top), Offset(left + cornerLen, top), cornerStroke.width)
        drawLine(cornerColor, Offset(left, top), Offset(left, top + cornerLen), cornerStroke.width)

        // Top-Right
        drawLine(cornerColor, Offset(right, top), Offset(right - cornerLen, top), cornerStroke.width)
        drawLine(cornerColor, Offset(right, top), Offset(right, top + cornerLen), cornerStroke.width)

        // Bottom-Left
        drawLine(cornerColor, Offset(left, bottom), Offset(left + cornerLen, bottom), cornerStroke.width)
        drawLine(cornerColor, Offset(left, bottom), Offset(left, bottom - cornerLen), cornerStroke.width)

        // Bottom-Right
        drawLine(cornerColor, Offset(right, bottom), Offset(right - cornerLen, bottom), cornerStroke.width)
        drawLine(cornerColor, Offset(right, bottom), Offset(right, bottom - cornerLen), cornerStroke.width)
    }
}
