package com.example.idlegame.ui.main

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.idlegame.BuildConfig
import com.example.idlegame.data.GameRepository
import com.example.idlegame.data.GameState
import com.example.idlegame.data.Material
import com.example.idlegame.data.Recipe
import com.example.idlegame.data.RecipeResult
import com.example.idlegame.network.ApiRepository
import com.example.idlegame.network.TokenManager
import com.example.idlegame.network.dataOrNull
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class MainViewModel(
    private val repository: GameRepository,
    private val apiRepository: ApiRepository,
    private val app: Application,
) : ViewModel() {

    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state

    private val _battleLog = MutableStateFlow<List<String>>(emptyList())
    val battleLog: StateFlow<List<String>> = _battleLog

    private val _isIdleSleeping = MutableStateFlow(false)
    val isIdleSleeping: StateFlow<Boolean> = _isIdleSleeping

    // 未受け取りのオフライン報酬。nullなら表示しない。
    private val _pendingOffline = MutableStateFlow<GameRepository.OfflineResult?>(null)
    val pendingOffline: StateFlow<GameRepository.OfflineResult?> = _pendingOffline

    private val _isStateLoaded = MutableStateFlow(false)
    val isStateLoaded: StateFlow<Boolean> = _isStateLoaded

    private var secondCount = 0
    private var minuteCount = 0
    private var tickerJob: Job? = null
    private var lastInteractionTime = System.currentTimeMillis()
    private var lastApiSaveMs = 0L

    init {
        viewModelScope.launch {
            val local = repository.load()
            val merged = mergeWithServerState(local)
            _state.value = merged
            _isStateLoaded.value = true
            checkOfflineReward()
            startTicking()
        }
    }

    private suspend fun mergeWithServerState(local: GameState): GameState {
        val token = TokenManager.getToken(app) ?: return local
        val response = apiRepository.loadGameState(token).dataOrNull() ?: return local
        val serverJson = response.stateJson ?: return local
        val server = apiRepository.deserializeState(serverJson) ?: return local
        return if (server.lastSaveTime > local.lastSaveTime) {
            repository.save(server)
            server
        } else local
    }

    // サーバー時刻を取得できた場合のみ報酬を計算する。オフライン・エラー時はスキップ。
    private suspend fun checkOfflineReward() {
        val serverEpochMs = apiRepository.getServerTime().dataOrNull() ?: return
        val offline = repository.calculateOfflineResult(_state.value, serverEpochMs)
        if (offline != null) _pendingOffline.value = offline
    }

    private fun todayString(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    private fun startTicking() {
        tickerJob?.cancel()
        lastInteractionTime = System.currentTimeMillis()
        tickerJob = viewModelScope.launch {
            while (isActive) {
                delay(1_000L)
                // DEBUGビルドのみ: 5分無操作でアイドルスリープ
                if (BuildConfig.DEBUG &&
                    System.currentTimeMillis() - lastInteractionTime > IDLE_TIMEOUT_MS) {
                    _isIdleSleeping.value = true
                    saveGame()
                    break
                }
                secondCount++
                processSecond()
                if (secondCount % 60 == 0) {
                    minuteCount++
                    processBattle()
                }
            }
        }
    }

    fun pauseTicking() {
        tickerJob?.cancel()
        tickerJob = null
        saveGame()
    }

    fun resumeTicking() {
        if (tickerJob?.isActive == true) return
        _isIdleSleeping.value = false
        viewModelScope.launch {
            checkOfflineReward()
            startTicking()
        }
    }

    fun recordInteraction() {
        lastInteractionTime = System.currentTimeMillis()
        if (_isIdleSleeping.value) {
            resumeTicking()
        }
    }

    // オフライン報酬を受け取る。doubled=trueなら広告視聴でコイン2倍。
    fun collectOfflineEarnings(doubled: Boolean) {
        val result = _pendingOffline.value ?: return
        _pendingOffline.value = null
        val coins = if (doubled) result.coins * 2L else result.coins
        val s = _state.value
        val newMilestone = (result.stageAfter / 100).toInt()
        val stonesEarned = maxOf(0, newMilestone - s.maxMilestoneReached)
        _state.value = s.copy(
            coins                = s.coins + coins,
            gems                 = s.gems + result.gems,
            stage                = result.stageAfter,
            maxMilestoneReached  = maxOf(s.maxMilestoneReached, newMilestone),
            prestigeStones       = s.prestigeStones + stonesEarned,
            totalCoinsEarned     = s.totalCoinsEarned + coins,
            totalEnemiesDefeated = s.totalEnemiesDefeated + result.minutesWon * GameState.ENEMIES_PER_MINUTE
        )
        saveGame()
    }

    // 緊急強化広告: 10分間攻撃力×2
    fun watchAttackBoostAd() {
        val s = _state.value
        val now = System.currentTimeMillis()
        if (now - s.lastAttackBoostAdTime < GameState.ATTACK_BOOST_AD_COOLDOWN_MS) return
        _state.value = s.copy(
            attackBoostEndTime    = now + GameState.ATTACK_BOOST_DURATION_MS,
            lastAttackBoostAdTime = now
        )
        recordDailyAd()
    }

    // 落下防止シールド広告: 次回の敗北ペナルティを1回無効
    fun watchShieldAd() {
        val s = _state.value
        val now = System.currentTimeMillis()
        if (now - s.lastShieldAdTime < GameState.SHIELD_AD_COOLDOWN_MS) return
        _state.value = s.copy(
            penaltyShieldActive = true,
            lastShieldAdTime    = now
        )
        recordDailyAd()
    }

    private fun processSecond() {
        val original = _state.value
        var s = original

        if (s.totalWeapons() < s.weaponSlots) {
            val star = generateWeaponStar(s)
            val w = s.weapons.toMutableMap()
            w[star] = (w[star] ?: 0) + 1
            s = s.copy(weapons = w)
        }

        // 自動合成: スロット満タン時に自動マージ（武器画面を閉じていても動作）
        if (s.isAutoMergeActive() && s.totalWeapons() >= s.weaponSlots) {
            val weapons = s.weapons.toMutableMap()
            mergeWeaponsMap(weapons)
            s = s.copy(weapons = weapons)
        }

        if (s.autoDeleteLevel > 0) {
            val toDelete = s.weapons.filterKeys { it <= s.autoDeleteLevel }
            if (toDelete.isNotEmpty()) {
                val iron   = toDelete.entries.filter { it.key in 1..3 }.sumOf { it.value }
                val silver = toDelete.entries.filter { it.key in 4..6 }.sumOf { it.value }
                val gold   = toDelete.entries.filter { it.key in 7..9 }.sumOf { it.value }
                s = s.copy(
                    weapons         = s.weapons.filterKeys { it > s.autoDeleteLevel },
                    ironFragments   = s.ironFragments + iron,
                    silverFragments = s.silverFragments + silver,
                    goldFragments   = s.goldFragments + gold
                )
            }
        }

        // プレイ時間を毎分カウント（5分ミッション用）
        if (secondCount % 60 == 0) {
            val today = todayString()
            val isToday = s.dailyDate == today
            val playSec = if (isToday) s.dailyPlaySeconds else 0
            if (playSec < 300) {
                s = s.copy(
                    dailyDate = today,
                    dailyPlaySeconds = minOf(300, playSec + 60)
                )
            }
        }

        if (s !== original) _state.value = s
    }

    private fun generateWeaponStar(s: GameState): Int {
        val unlocked = s.starGenLevels.entries
            .filter { it.value > 0 }
            .sortedByDescending { it.key }
        for ((star, level) in unlocked) {
            if (Random.nextFloat() < level / 100f) return star
        }
        return 1
    }

    fun setAutoDeleteLevel(level: Int) {
        val s = _state.value
        val clamped = level.coerceIn(0, s.maxAutoDeleteLevel())
        if (s.autoDeleteLevel != clamped) _state.value = s.copy(autoDeleteLevel = clamped)
    }

    fun unlockStar(star: Int) {
        val s = _state.value
        if (!s.canUnlockStar(star)) return
        val cost = s.starUnlockCost(star)
        if (s.gems < cost) return
        val newLevels = s.starGenLevels.toMutableMap()
        newLevels[star] = 1
        _state.value = s.copy(gems = s.gems - cost, starGenLevels = newLevels)
    }

    fun upgradeStarGen(star: Int) {
        val s = _state.value
        if (!s.isStarUnlocked(star)) return
        val currentLevel = s.starGenLevel(star)
        if (currentLevel >= 50) return
        val cost = s.starUpgradeCost(star, currentLevel)
        if (s.gems < cost) return
        val newLevels = s.starGenLevels.toMutableMap()
        newLevels[star] = currentLevel + 1
        _state.value = s.copy(gems = s.gems - cost, starGenLevels = newLevels)
    }

    fun addCoins(amount: Long) {
        _state.value = _state.value.copy(coins = _state.value.coins + amount)
    }

    private fun processBattle() {
        val s = _state.value
        val atk = s.totalAttack()
        val enemyHp = s.enemyHp()
        val stageBefore = s.stage
        val isBoss = s.isBossStage()
        val bossPrefix = if (isBoss) "★BOSS★ " else ""

        if (atk > enemyHp) {
            val tentative = stageBefore + GameState.ENEMIES_PER_MINUTE
            val nextBoss = if (isBoss) null
                else GameState.BOSS_STAGES.firstOrNull { it > stageBefore && it <= tentative }
            val stageAfter = nextBoss ?: tentative

            val baseCoins = GameState.ENEMIES_PER_MINUTE * enemyHp
            val coinBoost = if (s.isCraftCoinBoosted()) 2.0 else 1.0
            val battleCoins = (baseCoins * s.prestigeCoinMultiplier() * coinBoost).toLong()

            var gemsEarned = 0
            val gemRate = s.prestigeGemDropRate()
            repeat(GameState.ENEMIES_PER_MINUTE.toInt()) {
                if (Random.nextFloat() < gemRate) gemsEarned++
            }

            val newMilestone = (stageAfter / 100).toInt()
            val stonesEarned = maxOf(0, newMilestone - s.maxMilestoneReached)
            val newMaxMilestone = maxOf(s.maxMilestoneReached, newMilestone)

            val newlyDiscovered = GameState.RECIPES
                .filter { it.unlockStage <= stageAfter && it.id !in s.discoveredRecipeIds }
                .map { it.id }
            val updatedDiscovered = s.discoveredRecipeIds + newlyDiscovered

            val gemText        = if (gemsEarned      > 0) "  ジェム+$gemsEarned" else ""
            val stoneText      = if (stonesEarned     > 0) "  輝石+$stonesEarned" else ""
            val recipeText     = if (newlyDiscovered.isNotEmpty()) "  【新レシピ解放！】" else ""
            val bossCleared    = if (isBoss) " 【ボス撃破！】" else ""
            val boostTag       = if (s.isAttackBoosted()) " [強化中]" else ""
            val coinBoostTag   = if (s.isCraftCoinBoosted()) " [コインブースト中]" else ""
            val logEntry = "${minuteCount}分 ── $bossPrefix$stageBefore → $stageAfter  敵${GameState.ENEMIES_PER_MINUTE}体撃破！コイン+$battleCoins$gemText$stoneText$recipeText$bossCleared$boostTag$coinBoostTag"

            _state.value = s.copy(
                stage                = stageAfter,
                coins                = s.coins + battleCoins,
                gems                 = s.gems + gemsEarned,
                prestigeStones       = s.prestigeStones + stonesEarned,
                maxMilestoneReached  = newMaxMilestone,
                totalEnemiesDefeated = s.totalEnemiesDefeated + GameState.ENEMIES_PER_MINUTE,
                totalCoinsEarned     = s.totalCoinsEarned + battleCoins,
                discoveredRecipeIds  = updatedDiscovered
            )
            _battleLog.value = listOf(logEntry) + _battleLog.value.take(19)
        } else {
            val shieldActive = s.penaltyShieldActive
            val (stageAfter, failText) = if (shieldActive) {
                val label = if (isBoss) "ボスに敗北 【シールド発動！落下防止】" else "攻撃力不足 【シールド発動！落下防止】"
                stageBefore to label
            } else {
                // 序盤保護: ペナルティは現在ステージの50%か200の小さい方
                val penalty = minOf(GameState.STAGE_PENALTY, stageBefore / 2)
                val after = maxOf(1L, stageBefore - penalty)
                val label = if (isBoss) "ボスに敗北 (−${stageBefore - after}ステージ)"
                            else "攻撃力不足(−${stageBefore - after}ステージ)"
                after to label
            }
            val logEntry = "${minuteCount}分 ── $bossPrefix$stageBefore → $stageAfter  $failText"
            _state.value = s.copy(
                stage               = stageAfter,
                penaltyShieldActive = false  // シールドは使用済み or 元々なし
            )
            _battleLog.value = listOf(logEntry) + _battleLog.value.take(19)
        }

        saveGame()
    }

    fun upgradeCoinAttack() {
        val s = _state.value
        if (s.coinAttackLevel >= GameState.COIN_ATTACK_MAX_LEVEL) return
        val cost = s.coinAttackNextCost()
        if (s.coins < cost) return
        _state.value = s.copy(coins = s.coins - cost, coinAttackLevel = s.coinAttackLevel + 1)
    }

    fun buyPrestigeUpgrade(id: Int) {
        val s = _state.value
        val level = s.prestigeUpgradeLevel(id)
        if (level >= s.prestigeUpgradeMax(id)) return
        val cost = s.prestigeUpgradeCost(id)
        if (s.prestigeStones < cost) return
        val newUpgrades = s.prestigeUpgrades.toMutableMap()
        newUpgrades[id] = level + 1
        _state.value = s.copy(
            prestigeStones   = s.prestigeStones - cost,
            prestigeUpgrades = newUpgrades
        )
    }

    fun addGems(amount: Int) {
        _state.value = _state.value.copy(gems = _state.value.gems + amount)
    }

    // チュートリアル完了 + 初心者ボーナス（★2解放 + ジェム×50）
    fun markTutorialShown() {
        val s = _state.value
        if (s.tutorialShown) return
        val newStarLevels = s.starGenLevels.toMutableMap()
        val gemBonus = if (!s.isStarUnlocked(2)) {
            newStarLevels[2] = 1
            50
        } else 0
        _state.value = s.copy(
            tutorialShown = true,
            gems = s.gems + gemBonus,
            starGenLevels = newStarLevels
        )
        saveGame()
    }

    fun watchGemAd() {
        val s = _state.value
        val now = System.currentTimeMillis()
        if (now - s.lastGemAdTime < GameState.COIN_AD_COOLDOWN_MS) return
        val today = todayString()
        val watchedToday = if (s.gemAdLastDate == today) s.gemAdWatchedToday else 0
        if (watchedToday >= GameState.GEM_AD_DAILY_LIMIT) return
        _state.value = s.copy(
            gems              = s.gems + GameState.GEM_AD_REWARD,
            gemAdWatchedToday = watchedToday + 1,
            gemAdLastDate     = today,
            lastGemAdTime     = now
        )
        recordDailyAd()
    }

    fun watchCoinAd() {
        val s = _state.value
        val now = System.currentTimeMillis()
        if (now - s.lastCoinAdTime < GameState.COIN_AD_COOLDOWN_MS) return
        val reward = s.coinAdReward()
        _state.value = s.copy(
            coins            = s.coins + reward,
            lastCoinAdTime   = now,
            totalCoinsEarned = s.totalCoinsEarned + reward
        )
        recordDailyAd()
    }

    private val _pendingMinutes = MutableStateFlow(0)
    val pendingMinutes: StateFlow<Int> = _pendingMinutes

    private var synthJob: Job? = null

    fun addSynthesisMinute() {
        val s = _state.value
        if (s.gems < 10) return
        _state.value = s.copy(gems = s.gems - 10)
        _pendingMinutes.value++
        synthJob?.cancel()
        synthJob = viewModelScope.launch {
            delay(1_000L)
            applyGemSynthesis()
        }
    }

    private fun applyGemSynthesis() {
        val minutes = _pendingMinutes.value
        if (minutes == 0) return
        _pendingMinutes.value = 0
        val s = _state.value
        val weapons = s.weapons.toMutableMap()

        repeat(minutes * 60) {
            val star = generateWeaponStar(s)
            weapons[star] = (weapons[star] ?: 0) + 1
        }

        val overflow = weapons.values.sum() - s.weaponSlots
        if (overflow > 0) {
            var toRemove = overflow
            for (star in weapons.keys.sorted()) {
                if (toRemove <= 0) break
                val count = weapons[star] ?: 0
                val remove = minOf(count, toRemove)
                if (count - remove == 0) weapons.remove(star) else weapons[star] = count - remove
                toRemove -= remove
            }
        }

        _state.value = s.copy(weapons = weapons)
    }

    private fun mergeWeaponsMap(weapons: MutableMap<Int, Int>) {
        var changed = true
        while (changed) {
            changed = false
            for (level in weapons.keys.sorted()) {
                val count = weapons[level] ?: 0
                if (count >= 2) {
                    weapons[level] = count - 2
                    if (weapons[level] == 0) weapons.remove(level)
                    weapons[level + 1] = (weapons[level + 1] ?: 0) + 1
                    changed = true
                    break
                }
            }
        }
    }

    fun mergeWeapons() {
        val weapons = _state.value.weapons.toMutableMap()
        mergeWeaponsMap(weapons)
        val s = _state.value
        val today = todayString()
        val isToday = s.dailyDate == today
        val mergeCount = if (isToday) s.dailyMergeCount else 0
        _state.value = s.copy(
            weapons = weapons,
            dailyDate = today,
            dailyMergeCount = mergeCount + 1
        )
    }

    private fun recordDailyAd() {
        val s = _state.value
        val today = todayString()
        val isToday = s.dailyDate == today
        val count = if (isToday) s.dailyAdWatchCount else 0
        _state.value = s.copy(dailyDate = today, dailyAdWatchCount = count + 1)
    }

    // 無料で自動合成を3分間起動（1日3回・10分インターバル）
    fun activateAutoMergeFree() {
        val s = _state.value
        if (s.autoMergeOnCooldown()) return
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val usedToday = s.autoMergeFreeUsedToday(today)
        if (usedToday >= GameState.AUTO_MERGE_DAILY_FREE) return
        val now = System.currentTimeMillis()
        _state.value = s.copy(
            autoMergeEndTime       = now + GameState.AUTO_MERGE_DURATION_MS,
            autoMergeFreeUsesToday = usedToday + 1,
            autoMergeFreeLastDate  = today,
            autoMergeLastUsedTime  = now
        )
        saveGame()
    }

    // 広告視聴で自動合成を3分間起動（無料回数消化後・10分インターバル）
    fun activateAutoMergeAd() {
        val s = _state.value
        if (s.autoMergeOnCooldown()) return
        val today = todayString()
        if (s.autoMergeFreeRemainingToday(today) > 0) return  // まだ無料枠あり
        val now = System.currentTimeMillis()
        _state.value = s.copy(
            autoMergeEndTime      = now + GameState.AUTO_MERGE_DURATION_MS,
            autoMergeLastUsedTime = now
        )
        recordDailyAd()
        saveGame()
    }

    fun expandWeaponSlots(): Boolean {
        val s = _state.value
        if (s.weaponSlots >= 30) return false
        val cost = s.weaponSlotExpandCost()
        if (s.coins < cost) return false
        _state.value = s.copy(coins = s.coins - cost, weaponSlots = s.weaponSlots + 1)
        return true
    }

    fun claimAchievement(id: String) {
        val s = _state.value
        val def = GameState.ACHIEVEMENTS.find { it.id == id } ?: return
        val claimable = s.achievementClaimable(def)
        if (claimable <= 0) return
        val newClaimed = s.achievementsClaimed.toMutableMap()
        newClaimed[id] = (newClaimed[id] ?: 0) + claimable
        _state.value = s.copy(gems = s.gems + def.rewardGems * claimable, achievementsClaimed = newClaimed)
    }

    fun claimDailyMission(id: String) {
        val today = todayString()
        val s = _state.value
        val mission = s.dailyMissions(today).find { it.id == id } ?: return
        if (!mission.canClaim) return
        val isToday = s.dailyDate == today
        val currentClaimed = if (isToday && s.dailyMissionsClaimed.isNotEmpty())
            s.dailyMissionsClaimed.split(",").toMutableList() else mutableListOf()
        currentClaimed.add(id)
        _state.value = s.copy(
            gems                 = s.gems + mission.reward,
            dailyDate            = today,
            dailyMissionsClaimed = currentClaimed.joinToString(",")
        )
        saveGame()
    }

    fun craftRecipe(recipeId: String): Boolean {
        val recipe = GameState.RECIPES.find { it.id == recipeId } ?: return false
        val s = _state.value
        if (recipeId !in s.discoveredRecipeIds) return false
        if (!s.canCraft(recipe)) return false
        var next = deductMaterials(s, recipe)
        next = applyRecipeResult(next, recipe)
        _state.value = next
        saveGame()
        return true
    }

    private fun deductMaterials(s: GameState, recipe: Recipe): GameState {
        var result = s
        for (req in recipe.materials) {
            result = when (req.material) {
                Material.IRON_FRAGMENT   -> result.copy(ironFragments   = result.ironFragments   - req.amount)
                Material.SILVER_FRAGMENT -> result.copy(silverFragments = result.silverFragments - req.amount)
                Material.GOLD_FRAGMENT   -> result.copy(goldFragments   = result.goldFragments   - req.amount)
                Material.COIN            -> result.copy(coins           = result.coins           - req.amount.toLong())
                Material.GEM             -> result.copy(gems            = result.gems            - req.amount)
            }
        }
        return result
    }

    private fun applyRecipeResult(s: GameState, recipe: Recipe): GameState =
        when (val r = recipe.result) {
            is RecipeResult.AddWeapon -> {
                val weapons = s.weapons.toMutableMap()
                weapons[r.starLevel] = (weapons[r.starLevel] ?: 0) + 1
                s.copy(weapons = weapons)
            }
            is RecipeResult.CoinBoost ->
                s.copy(craftCoinBoostEndTime = System.currentTimeMillis() + r.durationMs)
        }

    fun resetGame() {
        _state.value = GameState()
        _battleLog.value = emptyList()
        saveGame()
    }

    fun saveGame() {
        viewModelScope.launch {
            val s = _state.value
            repository.save(s)
            val now = System.currentTimeMillis()
            if (now - lastApiSaveMs >= API_SAVE_INTERVAL_MS) {
                val token = TokenManager.getToken(app) ?: return@launch
                val result = apiRepository.saveGameState(token, s)
                if (result.dataOrNull() != null) lastApiSaveMs = now
            }
        }
    }

    override fun onCleared() {
        viewModelScope.launch(NonCancellable) { repository.save(_state.value) }
        super.onCleared()
    }

    companion object {
        private const val IDLE_TIMEOUT_MS = 5 * 60 * 1000L
        private const val API_SAVE_INTERVAL_MS = 5 * 60 * 1000L
    }

    class Factory(private val app: com.example.idlegame.IdleGameApp) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MainViewModel(app.repository, app.apiRepository, app) as T
    }
}
