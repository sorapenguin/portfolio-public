package com.example.hoshipost.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.hoshipost.ui.component.BoardCanvas
import com.example.hoshipost.ui.puzzle.PuzzleViewModel

@Composable
fun PuzzleScreen(
    stageName: String,
    viewModel: PuzzleViewModel,
    navController: NavController,
    modifier: Modifier = Modifier,
    onCleared: (
        stars: Int,
        playerSteps: Int,
        optimalSteps: Int,
        deliveredCount: Int,
        totalDeliveries: Int,
    ) -> Unit = { _, _, _, _, _ -> },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isCleared) {
        if (uiState.isCleared) {
            onCleared(
                uiState.stars ?: 1,
                uiState.playerSteps,
                uiState.board.optimalSteps,
                uiState.deliveredCount,
                uiState.totalDeliveries,
            )
        }
    }

    BackHandler {
        navController.popBackStack()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PuzzleTopBar(
            stageName = stageName,
            onExit = { navController.popBackStack() },
        )

        RouteInfoBar(
            steps = uiState.playerSteps,
            optimalSteps = uiState.board.optimalSteps,
            deliveredCount = uiState.deliveredCount,
            totalDeliveries = uiState.totalDeliveries,
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(uiState.board.width.toFloat() / uiState.board.height),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
            ) {
                BoardCanvas(
                    board = uiState.board,
                    route = uiState.route,
                    visitedDeliveryIds = uiState.visitedDeliveryIds,
                    modifier = Modifier.fillMaxSize(),
                    onDragStarted = viewModel::onDragStarted,
                    onDragMoved = viewModel::onDragMoved,
                    onDragEnded = viewModel::onDragEnded,
                )
            }
        }

        PuzzleBottomBar(
            canUndo = uiState.route.isNotEmpty(),
            onReset = viewModel::onResetRoute,
            onUndo = viewModel::onUndoRoute,
        )
    }

}

@Composable
private fun PuzzleTopBar(
    stageName: String,
    onExit: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stageName,
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onExit) {
            Text("終了")
        }
    }
}

@Composable
private fun RouteInfoBar(
    steps: Int,
    optimalSteps: Int,
    deliveredCount: Int,
    totalDeliveries: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("歩数: $steps")
        Text("最短目安: $optimalSteps")
        Text("配達 $deliveredCount/$totalDeliveries")
    }
}

@Composable
private fun PuzzleBottomBar(
    canUndo: Boolean,
    onReset: () -> Unit,
    onUndo: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            modifier = Modifier.weight(1f),
            onClick = onReset,
        ) {
            Text("リセット")
        }
        Button(
            modifier = Modifier.weight(1f),
            enabled = canUndo,
            onClick = onUndo,
        ) {
            Text("1手戻す")
        }
    }
}
