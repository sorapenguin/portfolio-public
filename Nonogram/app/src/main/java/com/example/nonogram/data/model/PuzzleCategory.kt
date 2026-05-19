package com.example.nonogram.data.model

enum class PuzzleCategory(val label: String, val rows: Int) {
    MINI("Mini 5×5", 5),
    NORMAL("Normal 10×10", 10),
    LARGE("Large 15×15", 15),
}
