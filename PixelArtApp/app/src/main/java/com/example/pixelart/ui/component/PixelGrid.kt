package com.example.pixelart.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.min

private val COLOR_BG_CELL = Color(0xFFCCCCCC)
private val COLOR_UNPAINTED = Color(0xFFF8F8F8)
private val COLOR_GRID_LINE = Color(0x55000000)
private val COLOR_NUMBER = Color(0xFF333333)

@Composable
fun PixelGrid(
    width: Int,
    height: Int,
    pixels: List<List<Int>>,   // 正解。0 = 背景セル（タップ不可）
    painted: List<List<Int>>,
    palette: List<Color>,
    onCellTouched: (row: Int, col: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()

    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val maxHeightPx = with(density) { maxHeight.toPx() }
        val cellSize = if (width > 0 && height > 0) {
            min(maxWidthPx / width, maxHeightPx / height)
        } else {
            0f
        }
        val gridWidth = cellSize * width
        val gridHeight = cellSize * height
        val offsetX = (maxWidthPx - gridWidth) / 2f
        val offsetY = (maxHeightPx - gridHeight) / 2f

        // セルサイズが変わったときだけ TextStyle を再生成
        val numberStyle = remember(cellSize, density) {
            TextStyle(
                fontSize = with(density) { (cellSize * 0.48f).toSp() },
                fontWeight = FontWeight.Bold,
                color = COLOR_NUMBER,
                textAlign = TextAlign.Center,
            )
        }

        fun Offset.toPlayableCell(): Pair<Int, Int>? {
            if (cellSize <= 0f) return null
            val localX = x - offsetX
            val localY = y - offsetY
            if (localX < 0f || localY < 0f || localX >= gridWidth || localY >= gridHeight) return null
            val row = (localY / cellSize).toInt()
            val col = (localX / cellSize).toInt()
            if (pixels.getOrNull(row)?.getOrNull(col) == 0) return null
            return row to col
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(width, height, cellSize) {
                    detectTapGestures { offset ->
                        offset.toPlayableCell()?.let { (row, col) -> onCellTouched(row, col) }
                    }
                }
                .pointerInput(width, height, cellSize) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            offset.toPlayableCell()?.let { (row, col) -> onCellTouched(row, col) }
                        },
                        onDrag = { change, _ ->
                            change.position.toPlayableCell()?.let { (row, col) -> onCellTouched(row, col) }
                        },
                    )
                },
        ) {
            if (cellSize <= 0f) return@Canvas

            repeat(height) { row ->
                repeat(width) { col ->
                    val target = pixels.getOrNull(row)?.getOrNull(col) ?: 0
                    val topLeft = Offset(offsetX + col * cellSize, offsetY + row * cellSize)
                    val cellSizeObj = Size(cellSize, cellSize)

                    if (target == 0) {
                        // 背景セル：グレー、枠線なし
                        drawRect(color = COLOR_BG_CELL, topLeft = topLeft, size = cellSizeObj)
                    } else {
                        val colorIndex = painted.getOrNull(row)?.getOrNull(col) ?: -1
                        if (colorIndex >= 0) {
                            // 塗り済み：パレット色で塗りつぶし（数字は消える）
                            drawRect(
                                color = palette.getOrElse(colorIndex) { Color.LightGray },
                                topLeft = topLeft,
                                size = cellSizeObj,
                            )
                        } else {
                            // 未塗り：白背景 + パレット番号
                            drawRect(color = COLOR_UNPAINTED, topLeft = topLeft, size = cellSizeObj)
                            val measured = textMeasurer.measure("$target", numberStyle)
                            val textOffset = Offset(
                                x = topLeft.x + (cellSize - measured.size.width) / 2f,
                                y = topLeft.y + (cellSize - measured.size.height) / 2f,
                            )
                            drawText(measured, topLeft = textOffset)
                        }
                        // プレイセル枠線
                        drawRect(color = COLOR_GRID_LINE, topLeft = topLeft, size = Size(cellSize, 1f))
                        drawRect(color = COLOR_GRID_LINE, topLeft = topLeft, size = Size(1f, cellSize))
                    }
                }
            }
        }
    }
}
