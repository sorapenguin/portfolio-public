package com.example.pixelart.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PaletteBar(
    palette: List<Color>,
    usedIndices: List<Int>,   // パズルで実際に使われる色のインデックスのみ
    selected: Int,
    onColorSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(usedIndices) { idx ->
            val color = palette.getOrElse(idx) { Color.Gray }
            val isSelected = idx == selected
            // 背景が明るければ黒文字、暗ければ白文字
            val textColor = if (color.luminance() > 0.4f) Color.Black else Color.White

            Box(
                modifier = Modifier
                    .size(if (isSelected) 52.dp else 44.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) Color.Black else Color.Gray,
                        shape = CircleShape,
                    )
                    .clickable { onColorSelected(idx) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "$idx",
                    color = textColor,
                    fontSize = if (isSelected) 13.sp else 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
