package com.example.nonogram.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nonogram.BuildConfig
import com.example.nonogram.data.model.CellState
import com.example.nonogram.data.model.parseSolution
import com.example.nonogram.domain.verifySolution
import com.example.nonogram.ui.component.NonogramGrid
import com.example.nonogram.viewmodel.NonogramViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NonogramScreen(
    viewModel: NonogramViewModel,
    puzzleId: Int,
    onNavigateBack: () -> Unit = {},
    showRewardedAd: ((onRewarded: () -> Unit, onDismissed: () -> Unit) -> Unit)? = null,
) {
    LaunchedEffect(puzzleId) { viewModel.loadPuzzle(puzzleId) }

    val uiState by viewModel.uiState.collectAsState()
    val puzzle = uiState.puzzle
    val rowHints = uiState.rowHints
    val colHints = uiState.colHints

    val solution = remember(puzzle) {
        puzzle?.solutionJson?.takeIf { it.isNotEmpty() }?.let { parseSolution(it) }
    }
    val emptyGrid = remember(puzzle) {
        List(puzzle?.rows ?: 5) { List(puzzle?.cols ?: 5) { CellState.EMPTY } }
    }
    val savedGrid = uiState.savedGrid

    var grid by remember(puzzle) { mutableStateOf(savedGrid ?: emptyGrid) }
    var isCleared by remember(puzzle) { mutableStateOf(false) }
    var fillMode by remember { mutableStateOf(true) }
    var history by remember(puzzle) { mutableStateOf(emptyList<List<List<CellState>>>()) }

    // クリア時に Room へ永続化（進捗は markCleared 内でクリアされる）
    LaunchedEffect(isCleared) {
        if (isCleared) puzzle?.let { viewModel.onPuzzleCleared(it.id) }
    }

    // グリッド変更を 500ms debounce して保存（クリア済みは保存しない）
    LaunchedEffect(grid) {
        if (puzzle != null && !isCleared) {
            delay(500L)
            viewModel.saveProgress(puzzle.id, grid)
        }
    }

    // Phase 5: リワード広告（ヒント回復用）
    LaunchedEffect(uiState.isAdPending) {
        if (uiState.isAdPending) {
            if (showRewardedAd != null) {
                showRewardedAd(viewModel::onAdRewarded, viewModel::onAdDismissed)
            } else {
                delay(3_000L)
                viewModel.onAdRewarded()
            }
        }
    }

    // クリア後の戻る広告
    LaunchedEffect(uiState.isBackAdPending) {
        if (uiState.isBackAdPending) {
            if (showRewardedAd != null) {
                showRewardedAd(viewModel::onBackAdCompleted, viewModel::onBackAdCompleted)
            } else {
                delay(3_000L)
                viewModel.onBackAdCompleted()
            }
        }
    }

    // スキップ券利用メッセージを 1.5 秒表示してから遷移
    LaunchedEffect(uiState.ticketUsedForBack) {
        if (uiState.ticketUsedForBack) {
            delay(1_500L)
            viewModel.onTicketUsedMessageShown()
        }
    }

    // navigateBack が立ったら画面遷移
    LaunchedEffect(uiState.navigateBack) {
        if (uiState.navigateBack) {
            viewModel.consumeNavigateBack()
            onNavigateBack()
        }
    }

    val wrongRows = remember(grid, solution) {
        if (solution == null) emptySet()
        else grid.indices.filter { r ->
            grid[r].indices.any { c ->
                val sol = solution[r][c]
                (grid[r][c] == CellState.FILLED && sol == 0) ||
                (grid[r][c] == CellState.MARKED && sol == 1)
            }
        }.toSet()
    }
    val wrongCols = remember(grid, solution) {
        if (solution == null) emptySet()
        else (grid.firstOrNull()?.indices ?: IntRange.EMPTY).filter { c ->
            grid.indices.any { r ->
                val sol = solution[r][c]
                (grid[r][c] == CellState.FILLED && sol == 0) ||
                (grid[r][c] == CellState.MARKED && sol == 1)
            }
        }.toSet()
    }

    fun pushHistory(snapshot: List<List<CellState>>) {
        history = (history + listOf(snapshot)).takeLast(30)
    }

    fun autoMarkIfComplete(currentGrid: List<List<CellState>>): List<List<CellState>> {
        fun lineHint(cells: List<CellState>): List<Int> {
            var count = 0
            val result = mutableListOf<Int>()
            for (cell in cells) {
                if (cell == CellState.FILLED) count++
                else if (count > 0) { result += count; count = 0 }
            }
            if (count > 0) result += count
            return result
        }

        var result = currentGrid
        val cols = result.firstOrNull()?.size ?: 0

        result.indices.forEach { r ->
            if (lineHint(result[r]) == rowHints[r]) {
                result = result.mapIndexed { ri, row ->
                    if (ri == r) row.map { if (it == CellState.EMPTY) CellState.MARKED else it } else row
                }
            }
        }
        (0 until cols).forEach { c ->
            if (lineHint(result.map { it[c] }) == colHints[c]) {
                result = result.mapIndexed { r, row ->
                    row.mapIndexed { ci, v -> if (ci == c && v == CellState.EMPTY) CellState.MARKED else v }
                }
            }
        }
        return result
    }

    val scope = rememberCoroutineScope()
    var dragIntent by remember { mutableStateOf<CellState?>(null) }
    val draggedCells = remember { mutableSetOf<Pair<Int, Int>>() }

    // 節電モード: 操作時刻を更新すると5分タイマーがリセットされる
    val idleMsState = remember { mutableStateOf(System.currentTimeMillis()) }
    val powerSavingState = remember { mutableStateOf(false) }
    var isPowerSaving by powerSavingState

    LaunchedEffect(idleMsState.value) {
        delay(5 * 60_000L)
        powerSavingState.value = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    idleMsState.value = System.currentTimeMillis()
                    powerSavingState.value = false
                }
            },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Nonogram") },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (!isCleared) {
                                scope.launch {
                                    puzzle?.let { p -> viewModel.saveProgress(p.id, grid) }
                                    onNavigateBack()
                                }
                            } else viewModel.requestBackNavigation()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                        }
                    },
                )
            },
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                when {
                    uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    uiState.error != null -> Text(
                        text = uiState.error!!,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                    puzzle == null -> Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("🎉", fontSize = 48.sp)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "すべてのパズルをクリアしました！",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "今後のアップデートをお楽しみに",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        ) {
                            val unfilledCorrect = remember(grid, solution) {
                                solution?.let { sol ->
                                    grid.indices.flatMap { r ->
                                        grid[r].indices.mapNotNull { c ->
                                            if (sol[r][c] == 1 && grid[r][c] != CellState.FILLED) r to c else null
                                        }
                                    }
                                } ?: emptyList()
                            }
                            if (uiState.hintCount > 0 || uiState.isAdPending) {
                                Button(
                                    onClick = {
                                        if (uiState.hintCount > 0) {
                                            val target = unfilledCorrect.randomOrNull() ?: return@Button
                                            pushHistory(grid)
                                            grid = grid.mapIndexed { ri, row ->
                                                row.mapIndexed { ci, v ->
                                                    if (ri == target.first && ci == target.second) CellState.FILLED else v
                                                }
                                            }
                                            grid = autoMarkIfComplete(grid)
                                            isCleared = verifySolution(grid, rowHints, colHints)
                                            viewModel.consumeHint()
                                        }
                                    },
                                    enabled = unfilledCorrect.isNotEmpty() && !isCleared && !uiState.isAdPending,
                                ) {
                                    Text(if (uiState.isAdPending) "広告読込中..." else "ヒント (${uiState.hintCount})")
                                }
                            } else {
                                if (uiState.skipTicketCount > 0) {
                                    OutlinedButton(
                                        onClick = { viewModel.useSkipTicketForHint() },
                                        enabled = unfilledCorrect.isNotEmpty() && !isCleared,
                                    ) { Text("スキップ券 (${uiState.skipTicketCount})") }
                                    Spacer(Modifier.width(4.dp))
                                }
                                OutlinedButton(
                                    onClick = { viewModel.requestAd() },
                                    enabled = unfilledCorrect.isNotEmpty() && !isCleared,
                                ) { Text("広告でヒント回復") }
                            }

                            OutlinedButton(
                                onClick = {
                                    val prev = history.lastOrNull() ?: return@OutlinedButton
                                    history = history.dropLast(1)
                                    grid = prev
                                    isCleared = false
                                },
                                enabled = history.isNotEmpty(),
                            ) { Text("戻す") }
                        }

                        // DEBUGビルドのみ: テストプレイ用の強制クリアボタン
                        if (BuildConfig.DEBUG) {
                            Spacer(Modifier.height(4.dp))
                            OutlinedButton(
                                onClick = {
                                    solution?.let { sol ->
                                        grid = grid.mapIndexed { r, row ->
                                            row.mapIndexed { c, _ ->
                                                if (sol[r][c] == 1) CellState.FILLED else CellState.EMPTY
                                            }
                                        }
                                    }
                                    isCleared = true
                                },
                                enabled = !isCleared,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF6B6B)),
                            ) { Text("[DEBUG] 強制クリア") }
                        }

                        Spacer(Modifier.height(12.dp))

                        if (isCleared) {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                                Text(
                                    "Puzzle Cleared!",
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                        }

                        BoxWithConstraints(modifier = Modifier.weight(1f)) {
                            val maxRowHintsCount = rowHints.maxOfOrNull { it.size } ?: 1
                            val maxColHintsCount = colHints.maxOfOrNull { it.size } ?: 1
                            val idealCell = 36f
                            val idealHint = 24f
                            val idealW = idealHint * maxRowHintsCount + idealCell * puzzle.cols
                            val idealH = idealHint * maxColHintsCount + idealCell * puzzle.rows
                            val scale = minOf(maxWidth.value / idealW, maxHeight.value / idealH, 1f)
                            val cellDp = (idealCell * scale).dp
                            val hintDp = (idealHint * scale).dp

                            NonogramGrid(
                                rows = puzzle.rows,
                                cols = puzzle.cols,
                                rowHints = rowHints,
                                colHints = colHints,
                                grid = grid,
                                wrongRows = wrongRows,
                                wrongCols = wrongCols,
                                cellDp = cellDp,
                                hintDp = hintDp,
                                onDragStart = {
                                    pushHistory(grid)
                                    dragIntent = null
                                    draggedCells.clear()
                                },
                                onCellInteract = { r, c ->
                                    if ((r to c) !in draggedCells) {
                                        draggedCells.add(r to c)
                                        val current = grid[r][c]
                                        val intent = dragIntent ?: run {
                                            val newIntent = if (fillMode) {
                                                if (current == CellState.FILLED) CellState.EMPTY else CellState.FILLED
                                            } else {
                                                if (current == CellState.MARKED) CellState.EMPTY else CellState.MARKED
                                            }
                                            dragIntent = newIntent
                                            newIntent
                                        }
                                        grid = grid.mapIndexed { ri, row ->
                                            row.mapIndexed { ci, v -> if (ri == r && ci == c) intent else v }
                                        }
                                        grid = autoMarkIfComplete(grid)
                                        isCleared = verifySolution(grid, rowHints, colHints)
                                    }
                                },
                            )
                        }

                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            if (fillMode) {
                                Button(onClick = { fillMode = true }) { Text("■", fontSize = 18.sp) }
                            } else {
                                OutlinedButton(onClick = { fillMode = true }) { Text("■", fontSize = 18.sp) }
                            }
                            Spacer(Modifier.width(12.dp))
                            if (!fillMode) {
                                Button(onClick = { fillMode = false }) { Text("×", fontSize = 18.sp) }
                            } else {
                                OutlinedButton(onClick = { fillMode = false }) { Text("×", fontSize = 18.sp) }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }

        // 広告再生オーバーレイ: isAdPending 中に全画面を覆う（ヒント回復用）
        if (uiState.isAdPending) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("▶ 動画再生中...", color = Color.White, fontSize = 22.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("広告視聴でヒント +10", color = Color.Gray, fontSize = 14.sp)
                }
            }
        }

        // 広告再生オーバーレイ: クリア後の戻る（広告なし遷移用）
        if (uiState.isBackAdPending) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("▶ 動画再生中...", color = Color.White, fontSize = 22.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("広告視聴後に戻ります", color = Color.Gray, fontSize = 14.sp)
                }
            }
        }

        // スキップ券利用メッセージ
        if (uiState.ticketUsedForBack) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("スキップ券が利用されました", color = Color.White, fontSize = 20.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("残り ${uiState.skipTicketCount} 枚", color = Color.Gray, fontSize = 14.sp)
                }
            }
        }

        // 節電モード: 5分無操作で全画面を覆う（広告表示中は除外）
        if (isPowerSaving && !uiState.isAdPending) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable {
                        idleMsState.value = System.currentTimeMillis()
                        powerSavingState.value = false
                    },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = puzzle?.title ?: "Nonogram",
                        color = Color.White.copy(alpha = 0.25f),
                        fontSize = 28.sp,
                    )
                    Text(
                        text = "タップして再開",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

