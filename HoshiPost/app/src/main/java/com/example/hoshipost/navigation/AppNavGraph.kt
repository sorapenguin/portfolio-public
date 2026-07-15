package com.example.hoshipost.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.hoshipost.data.ProgressDataStore
import com.example.hoshipost.domain.logic.BoardGenerator
import com.example.hoshipost.domain.model.Difficulty
import com.example.hoshipost.domain.model.StageProgress
import com.example.hoshipost.ui.puzzle.PuzzleViewModel
import com.example.hoshipost.ui.puzzle.PuzzleViewModelFactory
import com.example.hoshipost.ui.screen.PuzzleScreen
import com.example.hoshipost.ui.screen.ResultScreen
import com.example.hoshipost.ui.screen.StageSelectScreen
import com.example.hoshipost.ui.screen.TitleScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AppNavGraph(navController: NavHostController) {
    val context = LocalContext.current
    val progressRepository = remember { ProgressDataStore(context) }
    val boardGenerator = remember { BoardGenerator() }
    val scope = rememberCoroutineScope()

    var lastStageId by remember { mutableStateOf<String?>(null) }
    var progressList by remember { mutableStateOf<List<StageProgress>>(emptyList()) }
    var isProgressLoading by remember { mutableStateOf(true) }

    fun refreshProgress() {
        scope.launch {
            isProgressLoading = true
            progressList = progressRepository.loadAll()
            lastStageId = progressRepository.loadLastStageId()
            isProgressLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshProgress()
    }

    NavHost(navController = navController, startDestination = "title") {
        composable("title") {
            TitleScreen(
                lastStageId = lastStageId,
                onStart = { navController.navigate("stages") },
                onContinue = { stageId -> navController.navigate("puzzle/${stageId.toLongOrNull() ?: 1L}") },
            )
        }

        composable("stages") {
            LaunchedEffect(Unit) {
                refreshProgress()
            }
            StageSelectScreen(
                progressList = progressList,
                isLoading = isProgressLoading,
                onStageSelected = { seed -> navController.navigate("puzzle/$seed") },
                onRandomSelected = { navController.navigate("puzzle/${System.currentTimeMillis()}") },
            )
        }

        composable(
            route = "puzzle/{seed}",
            arguments = listOf(navArgument("seed") { type = NavType.LongType }),
        ) { backStackEntry ->
            val seed = backStackEntry.arguments?.getLong("seed") ?: 1L
            val board by produceBoard(boardGenerator, seed)
            val boardValue = board

            if (boardValue == null) {
                androidx.compose.material3.CircularProgressIndicator()
            } else {
                val viewModel: PuzzleViewModel = viewModel(
                    key = "puzzle-$seed",
                    factory = PuzzleViewModelFactory(boardValue, progressRepository),
                )
                PuzzleScreen(
                    stageName = if (seed > 0L && seed <= 10L) "Stage $seed" else "Random",
                    viewModel = viewModel,
                    navController = navController,
                    onCleared = { stars, playerSteps, optimalSteps, deliveredCount, totalDeliveries ->
                        navController.navigate(
                            "result/$seed/$stars/$playerSteps/$optimalSteps/$deliveredCount/$totalDeliveries",
                        ) {
                            launchSingleTop = true
                        }
                    },
                )
            }
        }

        composable(
            route = "result/{stageId}/{stars}/{playerSteps}/{optimalSteps}/{deliveredCount}/{totalDeliveries}",
            arguments = listOf(
                navArgument("stageId") { type = NavType.LongType },
                navArgument("stars") { type = NavType.IntType },
                navArgument("playerSteps") { type = NavType.IntType },
                navArgument("optimalSteps") { type = NavType.IntType },
                navArgument("deliveredCount") { type = NavType.IntType },
                navArgument("totalDeliveries") { type = NavType.IntType },
            ),
        ) { backStackEntry ->
            val stageId = backStackEntry.arguments?.getLong("stageId") ?: 1L
            ResultScreen(
                stars = backStackEntry.arguments?.getInt("stars") ?: 1,
                playerSteps = backStackEntry.arguments?.getInt("playerSteps") ?: 0,
                optimalSteps = backStackEntry.arguments?.getInt("optimalSteps") ?: 0,
                deliveredCount = backStackEntry.arguments?.getInt("deliveredCount") ?: 0,
                totalDeliveries = backStackEntry.arguments?.getInt("totalDeliveries") ?: 0,
                onNext = {
                    val nextStageId = if (stageId > 0L && stageId < 10L) stageId + 1L else System.currentTimeMillis()
                    navController.navigate("puzzle/$nextStageId") {
                        popUpTo("puzzle/{seed}") { inclusive = true }
                    }
                },
                onRetry = {
                    navController.navigate("puzzle/$stageId") {
                        popUpTo("puzzle/{seed}") { inclusive = true }
                    }
                },
                onStageSelect = {
                    navController.navigate("stages") {
                        popUpTo("title")
                        launchSingleTop = true
                    }
                },
            )
        }
    }
}

@Composable
private fun produceBoard(
    boardGenerator: BoardGenerator,
    seed: Long,
): androidx.compose.runtime.State<com.example.hoshipost.domain.model.Board?> {
    val boardState = remember(seed) {
        mutableStateOf<com.example.hoshipost.domain.model.Board?>(null)
    }

    LaunchedEffect(seed) {
        boardState.value = null
        boardState.value = withContext(Dispatchers.Default) {
            boardGenerator.generate(seed, Difficulty.NORMAL)
        }
    }

    return boardState
}
