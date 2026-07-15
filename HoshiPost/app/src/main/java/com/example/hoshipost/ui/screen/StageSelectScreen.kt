package com.example.hoshipost.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.hoshipost.domain.model.StageProgress

@Composable
fun StageSelectScreen(
    progressList: List<StageProgress>,
    isLoading: Boolean,
    onStageSelected: (Long) -> Unit,
    onRandomSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progressByStageId = progressList.associateBy { it.stageId }
    val stageIds = (1L..10L).toList()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "ステージ選択",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        if (isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
            ) {
                items(stageIds) { stageId ->
                    StageRow(
                        title = "Stage $stageId",
                        status = progressByStageId[stageId.toString()]?.let {
                            buildStarText(it.bestStars)
                        } ?: "未クリア",
                        onClick = { onStageSelected(stageId) },
                    )
                    Divider()
                }

                item {
                    StageRow(
                        title = "Random",
                        status = "ランダム生成",
                        onClick = onRandomSelected,
                    )
                }
            }
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onRandomSelected,
        ) {
            Text("ランダムステージ")
        }
    }
}

@Composable
private fun StageRow(
    title: String,
    status: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = status,
                style = MaterialTheme.typography.bodyLarge,
            )
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
