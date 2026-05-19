package com.example.nonogram.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nonogram.data.local.StaminaPreferences
import com.example.nonogram.data.model.Puzzle
import com.example.nonogram.data.model.PuzzleCategory
import com.example.nonogram.ui.component.BottomTab
import com.example.nonogram.ui.component.NonogramBottomBar
import com.example.nonogram.viewmodel.PuzzleListViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PuzzleListScreen(
    viewModel: PuzzleListViewModel,
    onPuzzleClick: (Int) -> Unit,
    onNavigateToShop: () -> Unit,
    onNavigateToSettings: () -> Unit,
    showRewardedAd: ((onRewarded: () -> Unit, onDismissed: () -> Unit) -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsState()

    // スタミナ広告シミュレーション
    LaunchedEffect(uiState.isStaminaAdPending) {
        if (uiState.isStaminaAdPending) {
            if (showRewardedAd != null) {
                showRewardedAd(viewModel::onStaminaAdRewarded, viewModel::onStaminaAdDismissed)
            } else {
                delay(3_000L)
                viewModel.onStaminaAdRewarded()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Nonogram")
                        Text(
                            "クリア: ${uiState.clearedCount}問",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    }
                })
            },
            bottomBar = {
                NonogramBottomBar(
                    selected = BottomTab.HOME,
                    onSelect = {
                        when (it) {
                            BottomTab.SHOP -> onNavigateToShop()
                            BottomTab.SETTINGS -> onNavigateToSettings()
                            else -> Unit
                        }
                    },
                )
            },
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {

                    // ログインボーナスバナー
                    if (uiState.loginBonusAmount > 0) {
                        LoginBonusBanner(
                            amount = uiState.loginBonusAmount,
                            onDismiss = { viewModel.dismissLoginBonus() },
                        )
                    }

                    StaminaBanner(
                        stamina = uiState.stamina,
                        minutesToNext = uiState.minutesToNextRecovery,
                        isStaminaAdPending = uiState.isStaminaAdPending,
                        skipTicketCount = uiState.skipTicketCount,
                        onWatchStaminaAd = { viewModel.requestStaminaAd() },
                        onUseSkipTicketForStamina = { viewModel.useSkipTicketForStamina() },
                    )

                    val categories = PuzzleCategory.entries
                    TabRow(selectedTabIndex = categories.indexOf(uiState.selectedCategory)) {
                        categories.forEach { category ->
                            Tab(
                                selected = uiState.selectedCategory == category,
                                onClick = { viewModel.selectCategory(category) },
                                text = { Text(category.label) },
                            )
                        }
                    }

                    if (uiState.puzzles.isEmpty() && uiState.isLoading) {
                        Box(Modifier.fillMaxSize()) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(uiState.puzzles) { puzzle ->
                                PuzzleCard(
                                    puzzle = puzzle,
                                    stamina = uiState.stamina,
                                    onUnlock = {
                                        if (viewModel.unlockPuzzle(puzzle.id)) onPuzzleClick(puzzle.id)
                                    },
                                    onPlay = { onPuzzleClick(puzzle.id) },
                                )
                            }
                        }
                    }
                }

                if (uiState.error != null) {
                    Text(
                        text = uiState.error!!,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        // スタミナ広告オーバーレイ
        if (uiState.isStaminaAdPending) {
            AdOverlay("広告視聴でスタミナ +${StaminaPreferences.AD_RECOVERY}")
        }
    }
}

@Composable
private fun LoginBonusBanner(amount: Int, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "🎁 ログインボーナス！スキップ券 ×$amount",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            TextButton(onClick = onDismiss) { Text("閉じる") }
        }
    }
}

@Composable
private fun StaminaBanner(
    stamina: Int,
    minutesToNext: Int,
    isStaminaAdPending: Boolean,
    skipTicketCount: Int,
    onWatchStaminaAd: () -> Unit,
    onUseSkipTicketForStamina: () -> Unit,
) {
    val staminaEmpty = stamina == 0
    val containerColor = if (staminaEmpty) MaterialTheme.colorScheme.errorContainer
    else MaterialTheme.colorScheme.secondaryContainer

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {

            // スタミナ行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "⚡ スタミナ  $stamina / ${StaminaPreferences.MAX}",
                    style = MaterialTheme.typography.titleSmall,
                )
                if (stamina < StaminaPreferences.MAX && minutesToNext > 0) {
                    Text(
                        "次回復: ${minutesToNext}分後",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // スタミナ=0 のときのみ回復ボタンを表示
            if (staminaEmpty) {
                Spacer(Modifier.height(8.dp))
                if (skipTicketCount > 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onUseSkipTicketForStamina,
                            modifier = Modifier.weight(1f),
                        ) { Text("スキップ券で回復 ($skipTicketCount)") }
                        Button(
                            onClick = onWatchStaminaAd,
                            enabled = !isStaminaAdPending,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                if (isStaminaAdPending) "読込中..."
                                else "スタミナ広告回復 (+${StaminaPreferences.AD_RECOVERY})"
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = onWatchStaminaAd,
                        enabled = !isStaminaAdPending,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (isStaminaAdPending) "広告読込中..."
                            else "スタミナ広告回復 (+${StaminaPreferences.AD_RECOVERY})"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdOverlay(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("▶ 動画再生中...", color = Color.White, fontSize = 22.sp)
            Spacer(Modifier.height(8.dp))
            Text(message, color = Color.Gray, fontSize = 14.sp)
        }
    }
}

@Composable
private fun PuzzleCard(
    puzzle: Puzzle,
    stamina: Int,
    onUnlock: () -> Unit,
    onPlay: () -> Unit,
) {
    val isLocked = !puzzle.isUnlocked
    val hasProgress = puzzle.progressJson.isNotEmpty()
    val containerColor = when {
        isLocked -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (!isLocked) Modifier.clickable(onClick = onPlay) else Modifier),
        elevation = CardDefaults.cardElevation(defaultElevation = if (!isLocked) 2.dp else 0.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${puzzle.rows}×${puzzle.cols}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!isLocked && hasProgress) {
                        Text(
                            "▶ 途中から再開",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                if (isLocked) {
                    OutlinedButton(
                        onClick = onUnlock,
                        enabled = stamina > 0,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (stamina > 0)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        ),
                    ) {
                        Text(if (stamina > 0) "⚡ 解除" else "⚡ 不足")
                    }
                }
            }
        }
    }
}
