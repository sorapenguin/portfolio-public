package com.example.nonogram.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.nonogram.ui.component.BottomTab
import com.example.nonogram.ui.component.NonogramBottomBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToShop: () -> Unit = {},
    onResetCache: () -> Unit = {},
) {
    var showConfirmDialog by remember { mutableStateOf(false) }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("キャッシュをリセット") },
            text = {
                Text(
                    "ローカルに保存されたパズルデータをすべて削除します。\n" +
                    "進捗・クリア記録も消去されます。\n\n" +
                    "リセット後はサーバーから再取得されます。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        onResetCache()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                ) { Text("リセット") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("キャンセル") }
            },
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("設定") }) },
        bottomBar = {
            NonogramBottomBar(
                selected = BottomTab.SETTINGS,
                onSelect = {
                    when (it) {
                        BottomTab.HOME -> onNavigateToHome()
                        BottomTab.SHOP -> onNavigateToShop()
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
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("遊び方", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary)
            HorizontalDivider()

            SettingsRow(label = "■ で塗りつぶし", value = "ヒントの数字を満たすようにマスを埋めます")
            SettingsRow(label = "× でマーク", value = "空白と確定したマスを × でメモできます")
            SettingsRow(label = "ヒント", value = "困ったときはヒントボタンで1マスのヒントをもらえます")
            SettingsRow(label = "スタミナ", value = "問題を開くとスタミナを消費します。時間経過で自然に回復します")
            SettingsRow(label = "スキップ券", value = "ログインボーナスや広告視聴で獲得。クリア後の広告をスキップできます")

            Spacer(Modifier.height(8.dp))
            Text("アプリ情報", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary)
            HorizontalDivider()
            SettingsRow(label = "バージョン", value = "1.0.0")

            Spacer(Modifier.height(8.dp))
            Text("データ管理", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary)
            HorizontalDivider()
            OutlinedButton(
                onClick = { showConfirmDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
            ) {
                Text("キャッシュをリセット")
            }
        }
    }
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
