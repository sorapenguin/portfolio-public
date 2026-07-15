package com.example.pixelart.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.pixelart.ui.game.GameScreen
import com.example.pixelart.ui.game.GameViewModel
import com.example.pixelart.ui.list.PuzzleListScreen
import com.example.pixelart.ui.list.PuzzleListViewModel

sealed class Screen(val route: String) {
    data object List : Screen("list")
    data object Game : Screen("game/{puzzleId}") {
        fun createRoute(id: Int) = "game/$id"
    }
}

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.List.route) {
        composable(Screen.List.route) {
            val viewModel: PuzzleListViewModel = hiltViewModel()
            PuzzleListScreen(viewModel = viewModel) { id ->
                navController.navigate(Screen.Game.createRoute(id))
            }
        }
        composable(
            route = Screen.Game.route,
            arguments = listOf(navArgument("puzzleId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getInt("puzzleId") ?: return@composable
            val viewModel: GameViewModel = hiltViewModel()
            GameScreen(
                viewModel = viewModel,
                puzzleId = id,
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
