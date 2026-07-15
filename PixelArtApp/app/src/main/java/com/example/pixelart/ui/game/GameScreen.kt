package com.example.pixelart.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pixelart.ui.component.PaletteBar
import com.example.pixelart.ui.component.PixelGrid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    viewModel: GameViewModel,
    puzzleId: Int,
    onNavigateBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val latestState by rememberUpdatedState(uiState)

    LaunchedEffect(puzzleId) {
        viewModel.loadPuzzle(puzzleId)
    }

    DisposableEffect(puzzleId) {
        onDispose {
            if (!latestState.isCleared) viewModel.saveProgress(puzzleId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.puzzle?.title ?: "PixelArt") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.error != null -> Text(
                    text = uiState.error ?: "",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.error,
                )
                uiState.puzzle != null -> {
                    val puzzle = uiState.puzzle!!
                    Column(modifier = Modifier.fillMaxSize()) {
                        PixelGrid(
                            width = puzzle.width,
                            height = puzzle.height,
                            pixels = uiState.pixels,
                            painted = uiState.painted,
                            palette = uiState.palette,
                            onCellTouched = viewModel::paintCell,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .padding(12.dp),
                        )
                        PaletteBar(
                            palette = uiState.palette,
                            usedIndices = uiState.usedColorIndices,
                            selected = uiState.selectedColorIndex,
                            onColorSelected = viewModel::selectColor,
                        )
                    }
                }
            }

            if (uiState.isCleared) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xAA000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "クリア！",
                            color = Color.White,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Button(
                            onClick = onNavigateBack,
                            modifier = Modifier.padding(top = 20.dp),
                        ) {
                            Text("一覧に戻る")
                        }
                    }
                }
            }
        }
    }
}
