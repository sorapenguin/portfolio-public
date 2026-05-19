package com.example.nonogram.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

enum class BottomTab { HOME, SHOP, SETTINGS }

@Composable
fun NonogramBottomBar(selected: BottomTab, onSelect: (BottomTab) -> Unit) {
    NavigationBar {
        NavigationBarItem(
            icon = { Icon(Icons.Default.Home, contentDescription = null) },
            label = { Text("ホーム") },
            selected = selected == BottomTab.HOME,
            onClick = { onSelect(BottomTab.HOME) },
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.ShoppingCart, contentDescription = null) },
            label = { Text("ショップ") },
            selected = selected == BottomTab.SHOP,
            onClick = { onSelect(BottomTab.SHOP) },
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = { Text("設定") },
            selected = selected == BottomTab.SETTINGS,
            onClick = { onSelect(BottomTab.SETTINGS) },
        )
    }
}
