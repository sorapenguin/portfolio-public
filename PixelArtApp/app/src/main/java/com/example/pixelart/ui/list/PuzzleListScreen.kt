package com.example.pixelart.ui.list

import android.graphics.Color.parseColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pixelart.data.local.PuzzleEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PuzzleListScreen(
    viewModel: PuzzleListViewModel,
    onPuzzleClick: (Int) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val sizes = PuzzleSize.entries
    val selectedIndex = sizes.indexOf(uiState.selectedSize)

    Scaffold(
        topBar = { TopAppBar(title = { Text("PixelArt") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TabRow(selectedTabIndex = selectedIndex) {
                sizes.forEachIndexed { index, size ->
                    Tab(
                        selected = index == selectedIndex,
                        onClick = { viewModel.selectSize(size) },
                        text = { Text(size.label) },
                    )
                }
            }

            when {
                uiState.isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                uiState.puzzles.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${uiState.selectedSize.label} のパズルを準備中...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.puzzles, key = { it.id }) { puzzle ->
                        PuzzleCard(puzzle = puzzle, onClick = { onPuzzleClick(puzzle.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PuzzleCard(
    puzzle: PuzzleEntity,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (puzzle.isCleared) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PuzzleThumbnail(
                    puzzle = puzzle,
                    modifier = Modifier
                        .size(84.dp)
                        .aspectRatio(1f),
                )
                if (puzzle.isCleared) {
                    Text(
                        text = "CLEAR",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Color(0xAA1B5E20))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Text(
                text = puzzle.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Badge { Text(puzzle.difficulty) }
                Badge { Text("Lv.${puzzle.difficultyLevel}") }
            }
        }
    }
}

@Composable
private fun PuzzleThumbnail(
    puzzle: PuzzleEntity,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val pixels = parsePixels(puzzle.pixelsJson)
        val palette = parsePalette(puzzle.paletteJson)
        if (pixels.isEmpty() || palette.isEmpty()) {
            drawPlaceholder()
        } else {
            val cellWidth = size.width / puzzle.width
            val cellHeight = size.height / puzzle.height
            pixels.forEachIndexed { row, cols ->
                cols.forEachIndexed { col, index ->
                    // index == 0 は背景色（薄いグレー）
                    val color = if (index == 0) Color(0xFFCCCCCC)
                    else palette.getOrElse(index) { Color.LightGray }
                    drawRect(
                        color = color,
                        topLeft = Offset(col * cellWidth, row * cellHeight),
                        size = Size(cellWidth, cellHeight),
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawPlaceholder() {
    val cell = size.width / 4f
    repeat(4) { row ->
        repeat(4) { col ->
            val shade = if ((row + col) % 2 == 0) Color(0xFFE5E7EB) else Color(0xFFD1D5DB)
            drawRect(
                color = shade,
                topLeft = Offset(col * cell, row * cell),
                size = Size(cell, cell),
            )
        }
    }
}

private fun parsePixels(json: String): List<List<Int>> {
    if (json.isBlank()) return emptyList()
    val type = object : TypeToken<List<List<Int>>>() {}.type
    return runCatching { Gson().fromJson<List<List<Int>>>(json, type) }.getOrDefault(emptyList())
}

private fun parsePalette(json: String): List<Color> {
    if (json.isBlank()) return emptyList()
    val type = object : TypeToken<List<String>>() {}.type
    return runCatching {
        Gson().fromJson<List<String>>(json, type).map { Color(parseColor(it)) }
    }.getOrDefault(emptyList())
}
