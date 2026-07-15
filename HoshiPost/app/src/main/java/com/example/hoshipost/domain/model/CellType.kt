package com.example.hoshipost.domain.model

sealed class CellType {
    data object Road : CellType()
    data object Wall : CellType()
    data object Start : CellType()
    data object Goal : CellType()
    data class DeliveryPoint(val id: Int, val label: String) : CellType()
}
