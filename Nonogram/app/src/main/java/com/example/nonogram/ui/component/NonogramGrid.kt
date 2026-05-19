package com.example.nonogram.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nonogram.data.model.CellState

private val CELL_DEFAULT = 36.dp
private val HINT_DEFAULT = 24.dp

@Composable
fun NonogramGrid(
    rows: Int,
    cols: Int,
    rowHints: List<List<Int>>,
    colHints: List<List<Int>>,
    grid: List<List<CellState>>,
    onDragStart: () -> Unit,
    onCellInteract: (row: Int, col: Int) -> Unit,
    wrongRows: Set<Int> = emptySet(),
    wrongCols: Set<Int> = emptySet(),
    cellDp: Dp = CELL_DEFAULT,
    hintDp: Dp = HINT_DEFAULT,
    modifier: Modifier = Modifier,
) {
    val maxRowHints = rowHints.maxOfOrNull { it.size } ?: 1
    val maxColHints = colHints.maxOfOrNull { it.size } ?: 1
    val textMeasurer = rememberTextMeasurer()

    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnCellInteract by rememberUpdatedState(onCellInteract)

    val CELL = cellDp
    val HINT = hintDp

    Canvas(
        modifier = modifier
            .size(
                HINT * maxRowHints + CELL * cols,
                HINT * maxColHints + CELL * rows,
            )
            .pointerInput(Unit) {
                val hintColPx = (HINT * maxRowHints).toPx()
                val hintRowPx = (HINT * maxColHints).toPx()
                val cellPx = CELL.toPx()

                fun offsetToCell(x: Float, y: Float): Pair<Int, Int>? {
                    val c = ((x - hintColPx) / cellPx).toInt()
                    val r = ((y - hintRowPx) / cellPx).toInt()
                    return if (r in 0 until rows && c in 0 until cols) r to c else null
                }

                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    currentOnDragStart()
                    offsetToCell(down.position.x, down.position.y)?.let { (r, c) ->
                        currentOnCellInteract(r, c)
                    }
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { change ->
                            change.consume()
                            offsetToCell(change.position.x, change.position.y)?.let { (r, c) ->
                                currentOnCellInteract(r, c)
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        val hintColPx = (HINT * maxRowHints).toPx()
        val hintRowPx = (HINT * maxColHints).toPx()
        val cellPx = CELL.toPx()
        val hintPx = HINT.toPx()
        val markPad = 6.dp.toPx()
        val markStroke = 2.dp.toPx()
        val thinStroke = 0.5.dp.toPx()
        val thickStroke = 2.dp.toPx()

        // White background
        drawRect(Color.White)

        // Wrong row/col highlights (drawn behind cells)
        for (r in wrongRows) {
            drawRect(
                color = Color(0xFFFF6B6B),
                topLeft = Offset(hintColPx, hintRowPx + r * cellPx),
                size = Size(cols * cellPx, cellPx),
                alpha = 0.3f,
            )
        }
        for (c in wrongCols) {
            drawRect(
                color = Color(0xFFFF6B6B),
                topLeft = Offset(hintColPx + c * cellPx, hintRowPx),
                size = Size(cellPx, rows * cellPx),
                alpha = 0.3f,
            )
        }

        // Cell contents
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val left = hintColPx + c * cellPx
                val top = hintRowPx + r * cellPx
                when (grid.getOrNull(r)?.getOrNull(c) ?: CellState.EMPTY) {
                    CellState.FILLED -> drawRect(
                        color = Color.Black,
                        topLeft = Offset(left, top),
                        size = Size(cellPx, cellPx),
                    )
                    CellState.MARKED -> {
                        drawLine(
                            color = Color.Red,
                            start = Offset(left + markPad, top + markPad),
                            end = Offset(left + cellPx - markPad, top + cellPx - markPad),
                            strokeWidth = markStroke,
                        )
                        drawLine(
                            color = Color.Red,
                            start = Offset(left + cellPx - markPad, top + markPad),
                            end = Offset(left + markPad, top + cellPx - markPad),
                            strokeWidth = markStroke,
                        )
                    }
                    CellState.EMPTY -> Unit
                }
            }
        }

        // Grid lines — vertical (thick every 5 columns)
        for (c in 0..cols) {
            val x = hintColPx + c * cellPx
            drawLine(
                color = Color.DarkGray,
                start = Offset(x, hintRowPx),
                end = Offset(x, hintRowPx + rows * cellPx),
                strokeWidth = if (c % 5 == 0) thickStroke else thinStroke,
            )
        }

        // Grid lines — horizontal (thick every 5 rows)
        for (r in 0..rows) {
            val y = hintRowPx + r * cellPx
            drawLine(
                color = Color.DarkGray,
                start = Offset(hintColPx, y),
                end = Offset(hintColPx + cols * cellPx, y),
                strokeWidth = if (r % 5 == 0) thickStroke else thinStroke,
            )
        }

        // Row hints (left of each row, centered per slot)
        for (r in 0 until rows) {
            val hints = rowHints.getOrNull(r) ?: continue
            val style = TextStyle(fontSize = 11.sp, color = if (r in wrongRows) Color.Red else Color.Black)
            val cellTop = hintRowPx + r * cellPx
            hints.forEachIndexed { i, n ->
                val measured = textMeasurer.measure(n.toString(), style)
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(
                        x = i * hintPx + (hintPx - measured.size.width) / 2f,
                        y = cellTop + (cellPx - measured.size.height) / 2f,
                    ),
                )
            }
        }

        // Col hints (above each column, bottom-aligned within hint area)
        for (c in 0 until cols) {
            val hints = colHints.getOrNull(c) ?: continue
            val style = TextStyle(fontSize = 11.sp, color = if (c in wrongCols) Color.Red else Color.Black)
            val cellLeft = hintColPx + c * cellPx
            val startSlot = maxColHints - hints.size
            hints.forEachIndexed { i, n ->
                val measured = textMeasurer.measure(n.toString(), style)
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(
                        x = cellLeft + (cellPx - measured.size.width) / 2f,
                        y = (startSlot + i) * hintPx + (hintPx - measured.size.height) / 2f,
                    ),
                )
            }
        }
    }
}
