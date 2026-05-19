package com.example.nonogram

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.nonogram.ui.screen.NonogramScreen
import com.example.nonogram.ui.screen.PuzzleListScreen
import com.example.nonogram.ui.screen.SettingsScreen
import com.example.nonogram.ui.screen.ShopScreen
import com.example.nonogram.viewmodel.NonogramViewModel
import com.example.nonogram.viewmodel.PuzzleListViewModel
import com.example.nonogram.worker.PuzzlePrefetchWorker
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var workManager: WorkManager

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 許可・拒否どちらでもゲームは続行できる */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        schedulePuzzlePrefetch()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // portfolio: 即時スキップ / storeDebug: 3秒フェイク / storeRelease: TODO 本物AdMob
                    val showRewardedAd: ((onRewarded: () -> Unit, onDismissed: () -> Unit) -> Unit)? = when {
                        !BuildConfig.ADS_ENABLED -> { onRewarded, _ -> onRewarded() }
                        BuildConfig.FAKE_AD_MODE -> null
                        else -> null
                    }

                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "puzzle_list") {

                        composable("puzzle_list") {
                            val vm: PuzzleListViewModel = hiltViewModel()
                            PuzzleListScreen(
                                viewModel = vm,
                                onPuzzleClick = { id -> navController.navigate("puzzle/$id") },
                                onNavigateToShop = {
                                    navController.navigate("shop") { launchSingleTop = true }
                                },
                                onNavigateToSettings = {
                                    navController.navigate("settings") { launchSingleTop = true }
                                },
                                showRewardedAd = showRewardedAd,
                            )
                        }

                        composable("shop") {
                            val puzzleListEntry = navController.getBackStackEntry("puzzle_list")
                            val vm: PuzzleListViewModel = hiltViewModel(puzzleListEntry)
                            ShopScreen(
                                viewModel = vm,
                                onNavigateToHome = {
                                    navController.navigate("puzzle_list") {
                                        popUpTo("puzzle_list") { inclusive = true }
                                        launchSingleTop = true
                                    }
                                },
                                onNavigateToSettings = {
                                    navController.navigate("settings") { launchSingleTop = true }
                                },
                                showRewardedAd = showRewardedAd,
                            )
                        }

                        composable("settings") {
                            val puzzleListEntry = navController.getBackStackEntry("puzzle_list")
                            val vm: PuzzleListViewModel = hiltViewModel(puzzleListEntry)
                            SettingsScreen(
                                onNavigateToHome = {
                                    navController.navigate("puzzle_list") {
                                        popUpTo("puzzle_list") { inclusive = true }
                                        launchSingleTop = true
                                    }
                                },
                                onNavigateToShop = {
                                    navController.navigate("shop") { launchSingleTop = true }
                                },
                                onResetCache = {
                                    vm.resetCache()
                                    navController.navigate("puzzle_list") {
                                        popUpTo("puzzle_list") { inclusive = true }
                                        launchSingleTop = true
                                    }
                                },
                            )
                        }

                        composable("puzzle/{puzzleId}") { backStack ->
                            val id = backStack.arguments?.getString("puzzleId")?.toIntOrNull()
                                ?: return@composable
                            val vm: NonogramViewModel = hiltViewModel()
                            NonogramScreen(
                                viewModel = vm,
                                puzzleId = id,
                                onNavigateBack = { navController.navigateUp() },
                                showRewardedAd = showRewardedAd,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun schedulePuzzlePrefetch() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<PuzzlePrefetchWorker>()
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniqueWork(
            PuzzlePrefetchWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
