package com.example.idlegame.data

data class GameState(
    val coins: Long = 0L,
    val gems: Int = 0,
    val lastSaveTime: Long = System.currentTimeMillis(),
    val weapons: Map<Int, Int> = emptyMap(),
    val weaponSlots: Int = 5,
    val stage: Long = 1L,
    val autoDeleteLevel: Int = 0,
    val starGenLevels: Map<Int, Int> = emptyMap(),  // star → 0=ロック, 1-50=確率レベル
    val coinAttackLevel: Int = 0,                    // 購入回数（0=未購入）
    val prestigeStones: Int = 0,
    val prestigeUpgrades: Map<Int, Int> = emptyMap(), // upgradeId → level
    val maxMilestoneReached: Int = 0,                  // 敗北農場防止: 到達済み最大(stage/100)
    val achievementsClaimed: Map<String, Int> = emptyMap(),
    val totalEnemiesDefeated: Long = 0L,
    val totalCoinsEarned: Long = 0L,
    val gemAdWatchedToday: Int = 0,
    val gemAdLastDate: String = "",
    val lastGemAdTime: Long = 0L,
    val lastCoinAdTime: Long = 0L,
    val attackBoostEndTime: Long = 0L,        // 緊急強化広告の終了時刻
    val penaltyShieldActive: Boolean = false, // 落下防止シールド（次回敗北1回無効）
    val lastAttackBoostAdTime: Long = 0L,
    val lastShieldAdTime: Long = 0L,
    val autoMergeEndTime: Long = 0L,          // 自動合成の終了時刻
    val autoMergeFreeUsesToday: Int = 0,
    val autoMergeFreeLastDate: String = "",
    val autoMergeLastUsedTime: Long = 0L,
    val tutorialShown: Boolean = false,
    // 欠片（オートデリート時に生成される素材）
    val ironFragments: Int = 0,
    val silverFragments: Int = 0,
    val goldFragments: Int = 0,
    // 解放済みレシピID
    val discoveredRecipeIds: Set<String> = emptySet(),
    // クラフト由来のコインブースト終了時刻
    val craftCoinBoostEndTime: Long = 0L,
    // デイリーミッション
    val dailyDate: String = "",
    val dailyMergeCount: Int = 0,
    val dailyPlaySeconds: Int = 0,
    val dailyAdWatchCount: Int = 0,
    val dailyMissionsClaimed: String = ""
) {
    fun totalWeapons(): Int = weapons.values.sum()
    fun isAttackBoosted(): Boolean = System.currentTimeMillis() < attackBoostEndTime
    fun boostRemainingMs(): Long = maxOf(0L, attackBoostEndTime - System.currentTimeMillis())
    fun isCraftCoinBoosted(): Boolean = System.currentTimeMillis() < craftCoinBoostEndTime
    fun craftCoinBoostRemainingMs(): Long = maxOf(0L, craftCoinBoostEndTime - System.currentTimeMillis())
    fun isAutoMergeActive(): Boolean = System.currentTimeMillis() < autoMergeEndTime
    fun autoMergeRemainingMs(): Long = maxOf(0L, autoMergeEndTime - System.currentTimeMillis())
    fun autoMergeFreeUsedToday(today: String): Int =
        if (autoMergeFreeLastDate == today) autoMergeFreeUsesToday else 0
    fun autoMergeFreeRemainingToday(today: String): Int =
        maxOf(0, AUTO_MERGE_DAILY_FREE - autoMergeFreeUsedToday(today))
    fun autoMergeOnCooldown(): Boolean =
        System.currentTimeMillis() - autoMergeLastUsedTime < AUTO_MERGE_COOLDOWN_MS
    fun autoMergeCooldownRemainingMs(): Long =
        maxOf(0L, AUTO_MERGE_COOLDOWN_MS - (System.currentTimeMillis() - autoMergeLastUsedTime))

    fun totalAttack(): Long {
        val base = weapons.entries.sumOf { (level, count) -> starAttack(level) * count } + coinAttackBonus()
        val boostMul = if (isAttackBoosted()) 2.0 else 1.0
        return (base * prestigeAttackMultiplier() * boostMul).toLong()
    }

    // ブーストなしの攻撃力（オフライン計算用）
    fun totalAttackBase(): Long {
        val base = weapons.entries.sumOf { (level, count) -> starAttack(level) * count } + coinAttackBonus()
        return (base * prestigeAttackMultiplier()).toLong()
    }
    fun weaponSlotExpandCost(): Long {
        val n = weaponSlots
        return when {
            n < 10 -> (n - 3) * 100L              // 200〜600（序盤ゆるめ）
            n < 20 -> (n - 7) * 1_000L            // 3k〜13k
            n < 35 -> (n - 15) * 20_000L          // 100k〜400k
            else   -> (n - 30) * 1_000_000L       // 5M〜（終盤）
        }
    }

    // coinAttackLevel 回購入済みのときの累計攻撃力ボーナス: 2^0+2^1+...+2^(n-1) = 2^n - 1
    fun coinAttackBonus(): Long = if (coinAttackLevel == 0) 0L else (1L shl coinAttackLevel) - 1
    // 次の購入コスト（Lv n+1 は 2^n コイン）
    fun coinAttackNextCost(): Long = 1L shl coinAttackLevel
    // 次の購入で得られる攻撃力増加量（コストと同値）
    fun coinAttackNextBonus(): Long = 1L shl coinAttackLevel
    fun bossMultiplier(): Int = when (stage) {
        100L   -> 2
        300L   -> 3
        500L   -> 5
        1000L  -> 7
        10000L -> 10
        else   -> 1
    }
    fun isBossStage(): Boolean = bossMultiplier() > 1
    fun enemyHp(): Long = stage * bossMultiplier()
    fun maxWeaponLevel(): Int = weapons.keys.maxOrNull() ?: 0
    fun coinAdReward(): Long = maxOf(1000L, maxMilestoneReached.toLong() * 100L * 10L)
    fun maxAutoDeleteLevel(): Int = maxOf(0, maxWeaponLevel() - 1)

    fun isStarUnlocked(star: Int): Boolean = (starGenLevels[star] ?: 0) > 0
    fun starGenLevel(star: Int): Int = starGenLevels[star] ?: 0
    fun canUnlockStar(star: Int): Boolean {
        if (star < 2 || isStarUnlocked(star)) return false
        return if (star == 2) true else starGenLevel(star - 1) >= 10
    }
    // Lv10未満は半額（初心者ボーナス）、Lv10以降は通常コスト
    fun starUpgradeCost(star: Int, currentLevel: Int): Int =
        (currentLevel + 1) * star * if (currentLevel < 10) 1 else 2
    fun starUnlockCost(star: Int): Int = star * 100

    // --- 恒久アップグレード ---
    fun prestigeUpgradeLevel(id: Int): Int = prestigeUpgrades[id] ?: 0
    fun prestigeUpgradeMax(id: Int): Int = when (id) {
        PRESTIGE_ATTACK    -> 20
        PRESTIGE_COIN      -> 10
        PRESTIGE_OFFLINE   -> 8
        PRESTIGE_GEM_DROP  -> 10
        else               -> 0
    }
    // 次レベル購入コスト（現在レベルから+1する費用）
    fun prestigeUpgradeCost(id: Int): Int {
        val lv = prestigeUpgradeLevel(id)
        return when (id) {
            PRESTIGE_OFFLINE -> (lv + 1) * 2   // 2, 4, 6...
            else             -> lv + 1          // 1, 2, 3...
        }
    }
    fun prestigeAttackMultiplier(): Double = 1.0 + 0.05 * prestigeUpgradeLevel(PRESTIGE_ATTACK)
    fun prestigeCoinMultiplier(): Double   = 1.0 + 0.10 * prestigeUpgradeLevel(PRESTIGE_COIN)
    fun prestigeOfflineHours(): Int        = 8 + prestigeUpgradeLevel(PRESTIGE_OFFLINE)
    fun prestigeGemDropRate(): Float       = GEM_DROP_CHANCE + 0.01f * prestigeUpgradeLevel(PRESTIGE_GEM_DROP)

    fun fragmentAmount(material: Material): Int = when (material) {
        Material.IRON_FRAGMENT   -> ironFragments
        Material.SILVER_FRAGMENT -> silverFragments
        Material.GOLD_FRAGMENT   -> goldFragments
        Material.COIN            -> coins.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        Material.GEM             -> gems
    }

    fun canCraft(recipe: Recipe): Boolean = recipe.materials.all { req ->
        when (req.material) {
            Material.IRON_FRAGMENT   -> ironFragments >= req.amount
            Material.SILVER_FRAGMENT -> silverFragments >= req.amount
            Material.GOLD_FRAGMENT   -> goldFragments >= req.amount
            Material.COIN            -> coins >= req.amount.toLong()
            Material.GEM             -> gems >= req.amount
        }
    }

    fun achievementTimesEarned(def: AchievementDef): Int {
        val times = when (def.statKey) {
            "enemies" -> (totalEnemiesDefeated / def.threshold).toInt()
            "stage"   -> (maxMilestoneReached.toLong() * 100L / def.threshold).toInt()
            "coins"   -> (totalCoinsEarned / def.threshold).toInt()
            else      -> 0
        }
        return if (def.oneTime) minOf(times, 1) else times
    }
    fun achievementClaimable(def: AchievementDef): Int =
        maxOf(0, achievementTimesEarned(def) - (achievementsClaimed[def.id] ?: 0))

    fun dailyMissions(today: String): List<DailyMission> {
        val isToday = dailyDate == today
        val mergeCount  = if (isToday) dailyMergeCount   else 0
        val playSec     = if (isToday) dailyPlaySeconds   else 0
        val adCount     = if (isToday) dailyAdWatchCount  else 0
        val claimedSet  = if (isToday && dailyMissionsClaimed.isNotEmpty())
            dailyMissionsClaimed.split(",").toSet() else emptySet()

        val adMission = when {
            "ad5"  in claimedSet -> DailyMission("ad10", "広告を10回視聴", minOf(adCount, 10), 10, 15, "ad10" in claimedSet)
            "ad1"  in claimedSet -> DailyMission("ad5",  "広告を5回視聴",  minOf(adCount,  5),  5,  8, "ad5"  in claimedSet)
            else                 -> DailyMission("ad1",  "広告を1回視聴",  minOf(adCount,  1),  1,  3, "ad1"  in claimedSet)
        }

        return listOf(
            DailyMission("merge10", "武器を10回合成", minOf(mergeCount, 10), 10, 5, "merge10" in claimedSet),
            DailyMission("play5m",  "5分プレイ",      minOf(playSec, 300), 300, 5, "play5m"  in claimedSet),
            adMission
        )
    }

    companion object {
        const val ENEMIES_PER_MINUTE = 60L
        const val STAGE_PENALTY = 200L
        const val GEM_DROP_CHANCE = 0.05f
        const val COIN_ATTACK_MAX_LEVEL = 62  // Long 溢れ防止上限

        val BOSS_STAGES = listOf(100L, 300L, 500L, 1000L, 10000L)

        data class DailyMission(
            val id: String,
            val title: String,
            val progress: Int,
            val target: Int,
            val reward: Int,
            val claimed: Boolean
        ) {
            val completed: Boolean get() = progress >= target
            val canClaim: Boolean get() = completed && !claimed
            val progressText: String get() = when (id) {
                "play5m" -> "${progress / 60} / 5 分"
                else     -> "$progress / $target 回"
            }
        }

        data class AchievementDef(
            val id: String,
            val title: String,
            val description: String,
            val threshold: Long,
            val rewardGems: Int,
            val statKey: String,
            val oneTime: Boolean = false
        )

        val ACHIEVEMENTS: List<AchievementDef> = listOf(
            AchievementDef("kill_1k",  "1000体撃破",   "敵1000体撃破ごと",       1_000L, 5,  "enemies"),
            AchievementDef("stage_ms", "ステージ到達", "ステージ100の倍数ごと",    100L,  10, "stage"),
        )

        const val GEM_AD_DAILY_LIMIT  = 5
        const val GEM_AD_REWARD       = 10
        const val COIN_AD_COOLDOWN_MS = 10 * 60 * 1000L
        const val AUTO_MERGE_DURATION_MS  = 3 * 60 * 1_000L   // 3分
        const val AUTO_MERGE_COOLDOWN_MS  = 10 * 60 * 1_000L  // 10分インターバル
        const val AUTO_MERGE_DAILY_FREE   = 3                  // 1日の無料回数
        const val MAX_WEAPON_STAR         = 99
        const val ATTACK_BOOST_DURATION_MS    = 10 * 60 * 1000L  // 10分
        const val ATTACK_BOOST_AD_COOLDOWN_MS = 15 * 60 * 1000L  // 15分クールダウン
        const val SHIELD_AD_COOLDOWN_MS       = 30 * 60 * 1000L  // 30分クールダウン

        val RECIPES: List<Recipe> = listOf(
            Recipe(
                id = "sword_iron",
                name = "試作剣",
                materials = listOf(MaterialReq(Material.IRON_FRAGMENT, 10)),
                result = RecipeResult.AddWeapon(5),
                unlockStage = 10L,
                hint = "オートデリートで★1〜3のウェポンを削除すると鉄の欠片が入手できます"
            ),
            Recipe(
                id = "sword_silver",
                name = "銀の剣",
                materials = listOf(MaterialReq(Material.SILVER_FRAGMENT, 5), MaterialReq(Material.COIN, 1_000)),
                result = RecipeResult.AddWeapon(15),
                unlockStage = 50L,
                hint = "オートデリートで★4〜6のウェポンを削除すると銀の欠片が入手できます"
            ),
            Recipe(
                id = "coin_boost",
                name = "コインブースト",
                materials = listOf(MaterialReq(Material.GOLD_FRAGMENT, 3), MaterialReq(Material.GEM, 5)),
                result = RecipeResult.CoinBoost(10 * 60 * 1_000L),
                unlockStage = 100L,
                hint = "オートデリートで★7〜9のウェポンを削除すると金の欠片が入手できます"
            ),
            Recipe(
                id = "sword_legend",
                name = "伝説の剣",
                materials = listOf(
                    MaterialReq(Material.IRON_FRAGMENT, 10),
                    MaterialReq(Material.SILVER_FRAGMENT, 10),
                    MaterialReq(Material.GOLD_FRAGMENT, 10)
                ),
                result = RecipeResult.AddWeapon(30),
                unlockStage = 200L,
                hint = "各種の欠片を集めて作る伝説の武器。オートデリートを活用して素材を集めましょう"
            )
        )

        const val PRESTIGE_ATTACK   = 1
        const val PRESTIGE_COIN     = 2
        const val PRESTIGE_OFFLINE  = 3
        const val PRESTIGE_GEM_DROP = 4

        fun starAttack(level: Int): Long {
            var atk = 10L
            repeat(level - 1) { atk = (atk * 2.2).toLong() }
            return atk
        }

        fun bossMultiplierFor(s: Long): Int = when (s) {
            100L   -> 2
            300L   -> 3
            500L   -> 5
            1000L  -> 7
            10000L -> 10
            else   -> 1
        }
    }
}
