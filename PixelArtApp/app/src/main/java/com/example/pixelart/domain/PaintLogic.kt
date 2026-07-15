package com.example.pixelart.domain

// pixels[r][c] == 0 は背景（非プレイセル）、1以上が塗るべきパレットインデックス
object PaintLogic {
    fun emptyPainted(width: Int, height: Int): List<List<Int>> =
        List(height) { List(width) { -1 } }

    fun isCleared(pixels: List<List<Int>>, painted: List<List<Int>>): Boolean =
        pixels.isNotEmpty() &&
            pixels.size == painted.size &&
            pixels.indices.all { row ->
                pixels[row].size == painted[row].size &&
                    pixels[row].indices.all { col ->
                        val target = pixels[row][col]
                        target == 0 || target == painted[row][col]
                    }
            }

    fun completionRate(pixels: List<List<Int>>, painted: List<List<Int>>): Float {
        var total = 0
        var correct = 0
        pixels.indices.forEach { row ->
            pixels[row].indices.forEach { col ->
                val target = pixels[row][col]
                if (target != 0) {
                    total++
                    if (painted.getOrNull(row)?.getOrNull(col) == target) correct++
                }
            }
        }
        return if (total == 0) 0f else correct.toFloat() / total
    }
}
