package com.example.hoshipost.ui.component

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import com.example.hoshipost.domain.model.Board
import com.example.hoshipost.domain.model.Cell
import com.example.hoshipost.domain.model.CellType
import com.example.hoshipost.domain.model.Position

@Composable
fun BoardCanvas(
    board: Board,
    route: List<Position>,
    visitedDeliveryIds: Set<Int>,
    modifier: Modifier = Modifier,
    onDragStarted: (Position) -> Unit,
    onDragMoved: (Position) -> Unit,
    onDragEnded: () -> Unit,
) {
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    Canvas(
        modifier = modifier
            .onSizeChanged { canvasSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(board) {
                detectDragGestures(
                    onDragStart = { offset ->
                        offsetToPosition(offset, canvasSize, board.width, board.height)
                            ?.let(onDragStarted)
                    },
                    onDrag = { change, _ ->
                        offsetToPosition(change.position, canvasSize, board.width, board.height)
                            ?.let(onDragMoved)
                    },
                    onDragEnd = onDragEnded,
                    onDragCancel = onDragEnded,
                )
            },
    ) {
        val cellWidth = size.width / board.width
        val cellHeight = size.height / board.height

        for (row in 0 until board.height) {
            for (col in 0 until board.width) {
                drawCell(
                    cell = board.cells[row][col],
                    left = col * cellWidth,
                    top = row * cellHeight,
                    width = cellWidth,
                    height = cellHeight,
                    visitedDeliveryIds = visitedDeliveryIds,
                )
            }
        }

        if (route.size >= 2) {
            for (index in 0 until route.lastIndex) {
                drawLine(
                    color = Color(0xFF2979FF),
                    start = route[index].toCenter(cellWidth, cellHeight),
                    end = route[index + 1].toCenter(cellWidth, cellHeight),
                    strokeWidth = cellWidth * 0.15f,
                    cap = StrokeCap.Round,
                )
            }
        }

        route.lastOrNull()?.let { position ->
            drawCircle(
                color = Color(0xFF2979FF),
                radius = cellWidth * 0.22f,
                center = position.toCenter(cellWidth, cellHeight),
            )
        }
    }
}

private fun offsetToPosition(
    offset: Offset,
    canvasSize: Size,
    boardWidth: Int,
    boardHeight: Int,
): Position? {
    if (canvasSize.width == 0f || canvasSize.height == 0f) return null

    val cellWidth = canvasSize.width / boardWidth
    val cellHeight = canvasSize.height / boardHeight
    val col = (offset.x / cellWidth).toInt()
    val row = (offset.y / cellHeight).toInt()

    if (row !in 0 until boardHeight || col !in 0 until boardWidth) return null
    return Position(row, col)
}

private fun DrawScope.drawCell(
    cell: Cell,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    visitedDeliveryIds: Set<Int>,
) {
    val background = when (cell.type) {
        CellType.Road -> Color(0xFFF5F0E0)
        CellType.Wall -> Color(0xFF888888)
        CellType.Start -> Color(0xFFDCEEFF)
        CellType.Goal -> Color(0xFFDCFFDC)
        is CellType.DeliveryPoint -> Color(0xFFFFECD5)
    }

    drawRect(
        color = background,
        topLeft = Offset(left, top),
        size = Size(width, height),
    )
    drawRect(
        color = Color(0x33000000),
        topLeft = Offset(left, top),
        size = Size(width, height),
        style = Stroke(width = 1f),
    )

    when (val type = cell.type) {
        CellType.Road -> Unit
        CellType.Wall -> drawWallLines(left, top, width, height)
        CellType.Start -> drawCenteredText("〒", left, top, width, height)
        CellType.Goal -> drawCenteredText("G", left, top, width, height)
        is CellType.DeliveryPoint -> drawDeliveryPoint(
            type = type,
            left = left,
            top = top,
            width = width,
            height = height,
            isVisited = type.id in visitedDeliveryIds,
        )
    }
}

private fun DrawScope.drawWallLines(left: Float, top: Float, width: Float, height: Float) {
    val inset = width * 0.18f
    drawLine(
        color = Color(0xFF666666),
        start = Offset(left + inset, top + inset),
        end = Offset(left + width - inset, top + height - inset),
        strokeWidth = 2f,
    )
    drawLine(
        color = Color(0xFF666666),
        start = Offset(left + width - inset, top + inset),
        end = Offset(left + inset, top + height - inset),
        strokeWidth = 2f,
    )
}

private fun DrawScope.drawDeliveryPoint(
    type: CellType.DeliveryPoint,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    isVisited: Boolean,
) {
    drawCircle(
        color = Color(0xFFFFB74D),
        radius = width * 0.24f,
        center = Offset(left + width / 2f, top + height / 2f),
    )
    drawCenteredText(type.label, left, top, width, height)

    if (isVisited) {
        drawText(
            text = "✓",
            x = left + width * 0.78f,
            y = top + height * 0.30f,
            textSize = width * 0.28f,
            color = android.graphics.Color.rgb(46, 125, 50),
            align = Paint.Align.CENTER,
        )
    }
}

private fun DrawScope.drawCenteredText(
    text: String,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
) {
    drawText(
        text = text,
        x = left + width / 2f,
        y = top + height / 2f,
        textSize = width * 0.36f,
        color = android.graphics.Color.rgb(40, 40, 40),
        align = Paint.Align.CENTER,
        centerVertically = true,
    )
}

private fun DrawScope.drawText(
    text: String,
    x: Float,
    y: Float,
    textSize: Float,
    color: Int,
    align: Paint.Align,
    centerVertically: Boolean = false,
) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        this.textSize = textSize
        textAlign = align
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    val drawY = if (centerVertically) {
        y - (paint.descent() + paint.ascent()) / 2f
    } else {
        y
    }

    drawContext.canvas.nativeCanvas.drawText(text, x, drawY, paint)
}

private fun Position.toCenter(cellWidth: Float, cellHeight: Float): Offset =
    Offset((col + 0.5f) * cellWidth, (row + 0.5f) * cellHeight)
