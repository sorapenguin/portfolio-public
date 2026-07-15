package com.example.hoshipost.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun ResultScreen(
    stars: Int,
    playerSteps: Int,
    optimalSteps: Int,
    deliveredCount: Int,
    totalDeliveries: Int,
    onNext: () -> Unit,
    onRetry: () -> Unit,
    onStageSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = "クリア！",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = buildStarText(stars),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "あなたの歩数: $playerSteps 歩",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "最短歩数: $optimalSteps 歩",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "配達完了: $deliveredCount / $totalDeliveries",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = onNext,
            ) {
                Text("次のステージ")
            }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = onRetry,
            ) {
                Text("もう一度")
            }
        }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onStageSelect,
        ) {
            Text("ステージ選択へ")
        }
    }
}

private fun buildStarText(stars: Int): String {
    val earnedStars = stars.coerceIn(1, 3)
    return buildString {
        repeat(earnedStars) { append("★") }
        repeat(3 - earnedStars) { append("☆") }
    }
}
