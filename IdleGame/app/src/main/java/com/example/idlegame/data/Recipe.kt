package com.example.idlegame.data

enum class Material(val label: String) {
    IRON_FRAGMENT("鉄の欠片"),
    SILVER_FRAGMENT("銀の欠片"),
    GOLD_FRAGMENT("金の欠片"),
    COIN("コイン"),
    GEM("ジェム")
}

data class MaterialReq(val material: Material, val amount: Int)

sealed class RecipeResult {
    data class AddWeapon(val starLevel: Int) : RecipeResult()
    data class CoinBoost(val durationMs: Long) : RecipeResult()
}

data class Recipe(
    val id: String,
    val name: String,
    val materials: List<MaterialReq>,
    val result: RecipeResult,
    val unlockStage: Long,
    val hint: String
) {
    val resultDescription: String
        get() = when (val r = result) {
            is RecipeResult.AddWeapon -> "★${r.starLevel}ウェポンを1個追加"
            is RecipeResult.CoinBoost -> "コイン獲得量×2 (${r.durationMs / 60_000}分間)"
        }
}
