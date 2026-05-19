package com.example.nonogram.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.example.nonogram.data.local.AdSkipTicketPreferences
import com.example.nonogram.ui.component.BottomTab
import com.example.nonogram.ui.component.NonogramBottomBar
import com.example.nonogram.viewmodel.PuzzleListViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopScreen(
    viewModel: PuzzleListViewModel,
    onNavigateToHome: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    showRewardedAd: ((onRewarded: () -> Unit, onDismissed: () -> Unit) -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isSkipTicketAdPending) {
        if (uiState.isSkipTicketAdPending) {
            if (showRewardedAd != null) {
                showRewardedAd(viewModel::onSkipTicketAdRewarded, viewModel::onSkipTicketAdDismissed)
            } else {
                delay(3_000L)
                viewModel.onSkipTicketAdRewarded()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text("ショップ") })
            },
            bottomBar = {
                NonogramBottomBar(
                    selected = BottomTab.SHOP,
                    onSelect = {
                        when (it) {
                            BottomTab.HOME -> onNavigateToHome()
                            BottomTab.SETTINGS -> onNavigateToSettings()
                            else -> Unit
                        }
                    },
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "スキップ券: ${uiState.skipTicketCount} / ${AdSkipTicketPreferences.MAX_COUNT}",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "今日: ${uiState.skipAdWatchedToday} / ${AdSkipTicketPreferences.SKIP_AD_DAILY_LIMIT}回",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "クリア後の広告をスキップできます。ログインボーナスや広告視聴で獲得できます。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.requestSkipTicketAd() },
                            enabled = uiState.canWatchSkipTicketAd && !uiState.isSkipTicketAdPending,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                when {
                                    uiState.isSkipTicketAdPending -> "読込中..."
                                    uiState.skipTicketCount >= AdSkipTicketPreferences.MAX_COUNT -> "スキップ券が満タンです"
                                    !uiState.canWatchSkipTicketAd -> "クールダウン中 / 本日の上限に達しました"
                                    else -> "広告でスキップ券 +1"
                                }
                            )
                        }
                    }
                }
            }
        }

        if (uiState.isSkipTicketAdPending) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("▶ 動画再生中...", color = Color.White, fontSize = 22.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("広告視聴でスキップ券 +1", color = Color.Gray, fontSize = 14.sp)
                }
            }
        }
    }
}
