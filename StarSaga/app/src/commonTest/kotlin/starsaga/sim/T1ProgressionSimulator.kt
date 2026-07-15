package starsaga.sim

import kotlin.math.roundToInt
import kotlin.random.Random
import starsaga.battle.AutoBattlePolicy
import starsaga.battle.BattleCompanionState
import starsaga.battle.BattleEngine
import starsaga.battle.BattlePhase
import starsaga.battle.BattleState
import starsaga.battle.EncounterResolver
import starsaga.battle.Leveling
import starsaga.battle.RecruitmentProgress
import starsaga.data.CompanionState
import starsaga.data.CreatureData
import starsaga.data.CreatureDatabase
import starsaga.data.CreatureRole
import starsaga.data.SkillDatabase
import starsaga.map.T1MapProgress

enum class ProgressionPolicy(val label: String) {
    FastClear("最短クリア優先"),
    SafeClear("安全攻略"),
    AutoCentric("AUTO中心"),
    CollectionFirst("収集優先"),
    NoWarp("M5-A ワープ不使用"),
    WarpSupport("M5-B 前哨地ワープ活用"),
    PrepFocused("M5-C 収集後準備重視"),
}

data class ProgressionResult(
    val policy: ProgressionPolicy,
    val seed: Int,
    val cleared: Boolean,
    val timeout: Boolean,
    val explorationSteps: Int,
    val encounters: Int,
    val normalBattles: Int,
    val normalWins: Int,
    val normalLosses: Int,
    val escapes: Int,
    val encounterCounts: Map<Int, Int>,
    val defeatsToJoin: Map<Int, Int>,
    val joinOrder: List<Int>,
    val luckBonuses: Int,
    val healUses: Int,
    val partyChanges: Int,
    val trainingUses: Int,
    val trainingHours: Int,
    val bossAttempts: Int,
    val bossLosses: Int,
    val finalParty: List<Int>,
    val totalBattles: Int,
    val totalActions: Int,
    val gameMinutes: Double,
    val impossibleReason: String?,
    val areaVisitCounts: Map<String, Int>,
    val areaMoves: Int,
    val lastMissingExtraSteps: Int,
    val lastMissingCreatureId: Int?,
    val duplicateBattles: Int,
    val battlesBeforeLuckJoined: Int,
    val battlesAfterLuckJoined: Int,
    val reachedOutpost: Boolean,
    val warpUnlocked: Boolean,
    val warpUses: Int,
    val bossDefeatTownWalkbacks: Int,
    val bossRetryExtraActions: Int,
)

data class ProgressionAggregate(
    val policy: ProgressionPolicy,
    val runs: Int,
    val clearRate: Double,
    val timeoutRate: Double,
    val minActions: Int,
    val medianActions: Int,
    val averageActions: Double,
    val p90Actions: Int,
    val maxActions: Int,
    val averageSteps: Double,
    val averageBattles: Double,
    val averageBossAttempts: Double,
    val averageBossLosses: Double,
    val averageHealUses: Double,
    val averageTrainingHours: Double,
    val fullCollectionRate: Double,
    val averageCollectionActions: Double,
    val finalPartySummary: Map<String, Int>,
    val timeoutExamples: List<ProgressionResult>,
    val averageAreaMoves: Double,
    val areaUseSummary: Map<String, Int>,
    val averageLastMissingExtraSteps: Double,
    val medianLastMissingExtraSteps: Int,
    val p90LastMissingExtraSteps: Int,
    val maxLastMissingExtraSteps: Int,
    val lastMissingCreatureSummary: Map<String, Int>,
    val lastMissingRoleSummary: Map<String, Int>,
    val averageDuplicateBattles: Double,
    val averageBattlesBeforeLuckJoined: Double,
    val averageBattlesAfterLuckJoined: Double,
    val outpostReachRate: Double,
    val warpUnlockRate: Double,
    val averageWarpUses: Double,
    val averageBossDefeatTownWalkbacks: Double,
    val averageBossRetryExtraActions: Double,
)

object T1ProgressionSimulator {
    const val MAX_EXPLORATION_STEPS = 3_000
    const val MAX_BOSS_ATTEMPTS = 8
    const val MAX_TOTAL_ACTIONS = 4_000
    const val MAX_BATTLE_TURNS = 80
    const val MAX_PARTY_SIZE = 6
    const val MAX_ACTIVE_SIZE = 3
    const val MAX_TRAINING_SIZE = 3
    const val RECRUIT_THRESHOLD = 5
    const val TRAINING_EXP_PER_HOUR = 8
    const val TRAINING_MAX_HOURS = 8

    fun run(policy: ProgressionPolicy, seed: Int): ProgressionResult {
        val random = Random(seed)
        val state = ProgressionState(policy, random, seed)
        state.ensureStarter()
        state.rebuildParty()

        while (!state.cleared && !state.timedOut()) {
            if (state.hasAllT1Creatures()) {
                state.prepareForBoss()
                state.fightBoss()
            } else {
                state.exploreOnce()
            }
        }

        return state.toResult()
    }

    fun aggregate(policy: ProgressionPolicy, results: List<ProgressionResult>): ProgressionAggregate {
        val runs = results.size.coerceAtLeast(1)
        val cleared = results.filter { it.cleared }
        val actionValues = cleared.map { it.totalActions }.sorted()
        val collectionActions = cleared.map { result ->
            // Approximation: all collection happens before first boss attempt in this simulator.
            result.explorationSteps + result.normalBattles
        }
        val finalParties = results
            .filter { it.cleared }
            .groupingBy { result ->
                result.finalParty.mapNotNull { CreatureDatabase.get(it)?.role?.code }.joinToString("+")
            }
            .eachCount()
        val lastMissingValues = results.map { it.lastMissingExtraSteps }.sorted()
        val lastMissingCreatures = results
            .mapNotNull { result -> result.lastMissingCreatureId?.let { CreatureDatabase.get(it)?.name } }
            .groupingBy { it }
            .eachCount()
        val lastMissingRoles = results
            .mapNotNull { result -> result.lastMissingCreatureId?.let { CreatureDatabase.get(it)?.role?.code } }
            .groupingBy { it }
            .eachCount()
        return ProgressionAggregate(
            policy = policy,
            runs = results.size,
            clearRate = results.count { it.cleared }.toDouble() / runs,
            timeoutRate = results.count { it.timeout }.toDouble() / runs,
            minActions = actionValues.firstOrNull() ?: 0,
            medianActions = percentile(actionValues, 0.50),
            averageActions = actionValues.averageOrZero(),
            p90Actions = percentile(actionValues, 0.90),
            maxActions = actionValues.lastOrNull() ?: 0,
            averageSteps = results.map { it.explorationSteps }.averageOrZero(),
            averageBattles = results.map { it.totalBattles }.averageOrZero(),
            averageBossAttempts = results.map { it.bossAttempts }.averageOrZero(),
            averageBossLosses = results.map { it.bossLosses }.averageOrZero(),
            averageHealUses = results.map { it.healUses }.averageOrZero(),
            averageTrainingHours = results.map { it.trainingHours }.averageOrZero(),
            fullCollectionRate = results.count { it.joinOrder.toSet().containsAll(CreatureDatabase.t1Creatures.map { creature -> creature.id }) }.toDouble() / runs,
            averageCollectionActions = collectionActions.averageOrZero(),
            finalPartySummary = finalParties,
            timeoutExamples = results.filter { it.timeout }.take(3),
            averageAreaMoves = results.map { it.areaMoves }.averageOrZero(),
            areaUseSummary = results
                .flatMap { result -> result.areaVisitCounts.entries }
                .groupBy({ it.key }, { it.value })
                .mapValues { (_, values) -> values.sum() },
            averageLastMissingExtraSteps = results.map { it.lastMissingExtraSteps }.averageOrZero(),
            medianLastMissingExtraSteps = percentile(lastMissingValues, 0.50),
            p90LastMissingExtraSteps = percentile(lastMissingValues, 0.90),
            maxLastMissingExtraSteps = lastMissingValues.lastOrNull() ?: 0,
            lastMissingCreatureSummary = lastMissingCreatures,
            lastMissingRoleSummary = lastMissingRoles,
            averageDuplicateBattles = results.map { it.duplicateBattles }.averageOrZero(),
            averageBattlesBeforeLuckJoined = results.map { it.battlesBeforeLuckJoined }.averageOrZero(),
            averageBattlesAfterLuckJoined = results.map { it.battlesAfterLuckJoined }.averageOrZero(),
            outpostReachRate = results.count { it.reachedOutpost }.toDouble() / runs,
            warpUnlockRate = results.count { it.warpUnlocked }.toDouble() / runs,
            averageWarpUses = results.map { it.warpUses }.averageOrZero(),
            averageBossDefeatTownWalkbacks = results.map { it.bossDefeatTownWalkbacks }.averageOrZero(),
            averageBossRetryExtraActions = results.map { it.bossRetryExtraActions }.averageOrZero(),
        )
    }

    private class ProgressionState(
        val policy: ProgressionPolicy,
        val random: Random,
        val seed: Int,
    ) {
        val companions = mutableListOf<CompanionState>()
        val partyIds = mutableListOf<String>()
        val activeIds = mutableListOf<String>()
        val befriended = mutableSetOf<Int>()
        val recruitProgress = mutableMapOf<Int, Int>()
        val defeatCounts = mutableMapOf<Int, Int>()
        val encounterCounts = mutableMapOf<Int, Int>()
        val defeatsToJoin = mutableMapOf<Int, Int>()
        val joinOrder = mutableListOf<Int>()
        var cleared = false
        var explorationSteps = 0
        var encounters = 0
        var normalBattles = 0
        var normalWins = 0
        var normalLosses = 0
        var luckBonuses = 0
        var healUses = 0
        var partyChanges = 0
        var trainingUses = 0
        var trainingHours = 0
        var bossAttempts = 0
        var bossLosses = 0
        var totalActions = 0
        var gameMinutes = 0.0
        var impossibleReason: String? = null
        val areaVisitCounts = mutableMapOf<String, Int>()
        var areaMoves = 0
        var lastExplorationMapId: String? = null
        var fourSpeciesStep: Int? = null
        var lastMissingCreatureId: Int? = null
        var duplicateBattles = 0
        var battlesBeforeLuckJoined = 0
        var battlesAfterLuckJoined = 0
        var reachedOutpost = false
        var warpUnlocked = false
        var warpUses = 0
        var bossDefeatTownWalkbacks = 0
        var bossRetryExtraActions = 0

        fun ensureStarter() {
            val starter = CreatureDatabase.get(1) ?: error("starter missing")
            companions += CompanionState("starter-1", starter.id, hp = starter.hp, mp = starter.mp, skillIds = SkillDatabase.initialSkillIdsFor(starter.id))
            befriended += starter.id
            joinOrder += starter.id
            partyIds += "starter-1"
            activeIds += "starter-1"
        }

        fun exploreOnce() {
            explorationSteps += 1
            totalActions += 1
            gameMinutes += EXPLORATION_ACTION_MINUTES
            val mapId = mapForNextExploration()
            areaVisitCounts[mapId] = areaVisitCounts.getOrDefault(mapId, 0) + 1
            if (lastExplorationMapId != null && lastExplorationMapId != mapId) areaMoves += 1
            lastExplorationMapId = mapId
            val encounter = EncounterResolver.roll(mapId, random)
            if (encounter.creature == null) return
            encounters += 1
            encounterCounts[encounter.creature.id] = encounterCounts.getOrDefault(encounter.creature.id, 0) + 1
            fightNormal(encounter.creature)
        }

        fun fightNormal(enemy: CreatureData) {
            rebuildParty()
            normalBattles += 1
            totalActions += 1
            val battle = runBattle(enemy, boss = false)
            gameMinutes += estimatedBattleMinutes(battle.turns)
            persistVitals(battle.state)
            if (battle.state.phase == BattlePhase.Victory) {
                normalWins += 1
                if (enemy.id in befriended) duplicateBattles += 1
                if (hasLuckJoined()) {
                    battlesAfterLuckJoined += 1
                } else {
                    battlesBeforeLuckJoined += 1
                }
                grantExp(enemy)
                progressRecruitment(enemy)
            } else {
                normalLosses += 1
                healParty()
            }
            if (shouldHealAfterBattle()) healParty()
        }

        fun prepareForBoss() {
            if (!reachedOutpost) {
                reachedOutpost = true
                totalActions += 1
                areaMoves += 1
            }
            if (!warpUnlocked) {
                warpUnlocked = true
                totalActions += 1
            }
            maybeUseOutpostWarpForPreparation()
            rebuildParty()
            if (policy != ProgressionPolicy.FastClear) {
                healParty()
            }
            when (policy) {
                ProgressionPolicy.FastClear -> if (bossLosses > 0) trainForHours(1)
                ProgressionPolicy.SafeClear -> when {
                    bossLosses > 0 -> trainForHours(4)
                    averageActiveLevel() < 4.0 -> trainForHours(2)
                }
                ProgressionPolicy.AutoCentric -> if (bossLosses > 0) trainForHours(2)
                ProgressionPolicy.CollectionFirst -> if (bossLosses > 0) trainForHours(1)
                ProgressionPolicy.NoWarp -> if (bossLosses > 0) trainForHours(1)
                ProgressionPolicy.WarpSupport -> if (bossLosses > 0) trainForHours(3)
                ProgressionPolicy.PrepFocused -> when {
                    bossAttempts == 0 && averageActiveLevel() < 4.0 -> trainForHours(2)
                    bossLosses > 0 -> trainForHours(3)
                }
            }
            rebuildParty()
        }

        fun fightBoss() {
            if (bossAttempts >= MAX_BOSS_ATTEMPTS) {
                impossibleReason = "boss attempts exceeded"
                return
            }
            bossAttempts += 1
            totalActions += 1
            val battle = runBattle(CreatureDatabase.t1Boss, boss = true)
            gameMinutes += estimatedBattleMinutes(battle.turns)
            persistVitals(battle.state)
            if (battle.state.phase == BattlePhase.Victory) {
                grantExp(CreatureDatabase.t1Boss)
                cleared = true
            } else {
                bossLosses += 1
                bossRetryExtraActions += 1
                healParty()
            }
        }

        fun hasAllT1Creatures(): Boolean =
            CreatureDatabase.t1Creatures.all { it.id in befriended }

        fun timedOut(): Boolean {
            val reason = when {
                explorationSteps >= MAX_EXPLORATION_STEPS -> "exploration steps exceeded"
                totalActions >= MAX_TOTAL_ACTIONS -> "total actions exceeded"
                bossAttempts >= MAX_BOSS_ATTEMPTS && !cleared -> "boss attempts exceeded"
                companions.any { it.hp < 0 || it.mp < 0 || it.exp < 0 } -> "invalid companion value"
                partyIds.any { id -> companions.none { it.instanceId == id } } -> "invalid party member"
                else -> null
            }
            if (reason != null) impossibleReason = reason
            return reason != null
        }

        fun toResult(): ProgressionResult =
            ProgressionResult(
                policy = policy,
                seed = seed,
                cleared = cleared,
                timeout = !cleared,
                explorationSteps = explorationSteps,
                encounters = encounters,
                normalBattles = normalBattles,
                normalWins = normalWins,
                normalLosses = normalLosses,
                escapes = 0,
                encounterCounts = encounterCounts.toMap(),
                defeatsToJoin = defeatsToJoin.toMap(),
                joinOrder = joinOrder.toList(),
                luckBonuses = luckBonuses,
                healUses = healUses,
                partyChanges = partyChanges,
                trainingUses = trainingUses,
                trainingHours = trainingHours,
                bossAttempts = bossAttempts,
                bossLosses = bossLosses,
                finalParty = activeIds.mapNotNull { id -> companions.firstOrNull { it.instanceId == id }?.creatureId },
                totalBattles = normalBattles + bossAttempts,
                totalActions = totalActions,
                gameMinutes = gameMinutes,
                impossibleReason = impossibleReason,
                areaVisitCounts = areaVisitCounts.toMap(),
                areaMoves = areaMoves,
                lastMissingExtraSteps = fourSpeciesStep?.let { explorationSteps - it } ?: 0,
                lastMissingCreatureId = lastMissingCreatureId,
                duplicateBattles = duplicateBattles,
                battlesBeforeLuckJoined = battlesBeforeLuckJoined,
                battlesAfterLuckJoined = battlesAfterLuckJoined,
                reachedOutpost = reachedOutpost,
                warpUnlocked = warpUnlocked,
                warpUses = warpUses,
                bossDefeatTownWalkbacks = bossDefeatTownWalkbacks,
                bossRetryExtraActions = bossRetryExtraActions,
            )

        private fun runBattle(enemy: CreatureData, boss: Boolean): SimBattleOutcome {
            var state = battleState(enemy)
            var turns = 0
            while (state.phase == BattlePhase.PlayerTurn && turns < MAX_BATTLE_TURNS) {
                turns += 1
                val afterPlayer = when (policy) {
                    ProgressionPolicy.FastClear -> attackFirst(state)
                    else -> AutoBattlePolicy.chooseSkill(state)?.let { BattleEngine.useSkill(state, it.casterInstanceId, it.skill, random) }
                        ?: BattleEngine.playerAttack(state, random)
                }
                state = if (afterPlayer.phase == BattlePhase.EnemyTurn) {
                    BattleEngine.enemyAttack(afterPlayer)
                } else {
                    afterPlayer
                }
            }
            if (turns >= MAX_BATTLE_TURNS && state.phase == BattlePhase.PlayerTurn) {
                impossibleReason = if (boss) "boss battle turn limit" else "normal battle turn limit"
            }
            return SimBattleOutcome(state, turns)
        }

        private fun attackFirst(state: BattleState): BattleState {
            val choice = state.activeCompanions
                .filter { it.currentHp > 0 }
                .flatMap { companion ->
                    companion.skillIds.mapNotNull { skillId ->
                        val skill = SkillDatabase.get(skillId) ?: return@mapNotNull null
                        if (skill.kind == starsaga.data.SkillKind.Attack && companion.currentMp >= skill.mpCost) {
                            AutoBattleChoice(companion.instanceId, skill)
                        } else {
                            null
                        }
                    }
                }
                .firstOrNull()
            return choice?.let { BattleEngine.useSkill(state, it.instanceId, it.skill, random) }
                ?: BattleEngine.playerAttack(state, random)
        }

        private fun battleState(enemy: CreatureData): BattleState =
            BattleState(
                enemy = enemy,
                enemyCurrentHp = enemy.hp,
                enemyMaxHp = enemy.hp,
                activeCompanions = activeIds.mapNotNull { id ->
                    val companion = companions.firstOrNull { it.instanceId == id } ?: return@mapNotNull null
                    val creature = CreatureDatabase.get(companion.creatureId) ?: return@mapNotNull null
                    BattleCompanionState(
                        instanceId = companion.instanceId,
                        name = creature.name,
                        role = creature.role,
                        attack = Leveling.attackWithLevel(creature.attack, companion.level),
                        defense = Leveling.defenseWithLevel(creature.defense, companion.level),
                        currentHp = companion.hp.coerceIn(0, Leveling.maxHp(creature.hp, companion.level)),
                        maxHp = Leveling.maxHp(creature.hp, companion.level),
                        currentMp = companion.mp.coerceIn(0, Leveling.maxMp(creature.mp, companion.level)),
                        maxMp = Leveling.maxMp(creature.mp, companion.level),
                        skillIds = companion.skillIds,
                    )
                },
                message = "${enemy.name}が現れた！",
                phase = BattlePhase.PlayerTurn,
            )

        private fun persistVitals(state: BattleState) {
            state.activeCompanions.forEach { battle ->
                val index = companions.indexOfFirst { it.instanceId == battle.instanceId }
                if (index >= 0) {
                    val current = companions[index]
                    companions[index] = current.copy(hp = battle.currentHp.coerceAtLeast(0), mp = battle.currentMp.coerceAtLeast(0))
                }
            }
        }

        private fun grantExp(enemy: CreatureData) {
            activeIds.forEach { id ->
                val index = companions.indexOfFirst { it.instanceId == id }
                if (index >= 0) companions[index] = Leveling.grantExp(companions[index], enemy.expReward).companion
            }
        }

        private fun progressRecruitment(enemy: CreatureData) {
            if (enemy.id in befriended) return
            val current = recruitProgress.getOrDefault(enemy.id, 0)
            val progress = RecruitmentProgress.advance(current, RECRUIT_THRESHOLD, hasLuckInParty(), random)
            if (progress.luckBonus) luckBonuses += 1
            recruitProgress[enemy.id] = progress.after
            defeatCounts[enemy.id] = defeatCounts.getOrDefault(enemy.id, 0) + 1
            if (progress.completed) addCompanion(enemy)
        }

        private fun addCompanion(creature: CreatureData) {
            val instanceId = "mem-${creature.id}-${companions.size + 1}"
            befriended += creature.id
            joinOrder += creature.id
            if (befriended.size == CreatureDatabase.t1Creatures.size - 1 && fourSpeciesStep == null) {
                fourSpeciesStep = explorationSteps
                lastMissingCreatureId = CreatureDatabase.t1Creatures.firstOrNull { it.id !in befriended }?.id
            }
            defeatsToJoin[creature.id] = defeatCounts.getOrDefault(creature.id, 0)
            companions += CompanionState(instanceId, creature.id, hp = creature.hp, mp = creature.mp, skillIds = SkillDatabase.initialSkillIdsFor(creature.id))
            if (partyIds.size < MAX_PARTY_SIZE) partyIds += instanceId
            rebuildParty()
        }

        fun rebuildParty() {
            val before = activeIds.toList()
            val ordered = companions.sortedWith(compareByDescending<CompanionState> { scoreForPolicy(it) }.thenByDescending { it.level })
            partyIds.clear()
            partyIds += ordered.take(MAX_PARTY_SIZE).map { it.instanceId }
            activeIds.clear()
            activeIds += partyIds.take(MAX_ACTIVE_SIZE)
            if (before.isNotEmpty() && before != activeIds) partyChanges += 1
        }

        private fun scoreForPolicy(companion: CompanionState): Int {
            val creature = CreatureDatabase.get(companion.creatureId) ?: return 0
            val roleScore = when (policy) {
                ProgressionPolicy.FastClear -> when (creature.role) {
                    CreatureRole.ATCK -> 60
                    CreatureRole.AREA -> 55
                    CreatureRole.DEFN -> 35
                    CreatureRole.HEAL -> 30
                    CreatureRole.LUCK -> 20
                }
                ProgressionPolicy.SafeClear -> when (creature.role) {
                    CreatureRole.DEFN -> 65
                    CreatureRole.HEAL -> 60
                    CreatureRole.ATCK -> 50
                    CreatureRole.AREA -> 45
                    CreatureRole.LUCK -> 30
                }
                ProgressionPolicy.AutoCentric -> when (creature.role) {
                    CreatureRole.ATCK -> 60
                    CreatureRole.DEFN -> 55
                    CreatureRole.HEAL -> 50
                    CreatureRole.AREA -> 45
                    CreatureRole.LUCK -> 25
                }
                ProgressionPolicy.CollectionFirst -> when {
                    !hasAllT1Creatures() && creature.role == CreatureRole.LUCK -> 80
                    creature.role == CreatureRole.ATCK -> 55
                    creature.role == CreatureRole.DEFN -> 50
                    creature.role == CreatureRole.HEAL -> 45
                    creature.role == CreatureRole.AREA -> 40
                    else -> 20
                }
                ProgressionPolicy.NoWarp -> when (creature.role) {
                    CreatureRole.ATCK -> 60
                    CreatureRole.DEFN -> 50
                    CreatureRole.HEAL -> 45
                    CreatureRole.AREA -> 40
                    CreatureRole.LUCK -> 25
                }
                ProgressionPolicy.WarpSupport,
                ProgressionPolicy.PrepFocused -> when (creature.role) {
                    CreatureRole.DEFN -> 65
                    CreatureRole.HEAL -> 60
                    CreatureRole.ATCK -> 55
                    CreatureRole.AREA -> 45
                    CreatureRole.LUCK -> 30
                }
            }
            return roleScore + companion.level * 5
        }

        private fun hasLuckInParty(): Boolean =
            partyIds.any { id ->
                val companion = companions.firstOrNull { it.instanceId == id } ?: return@any false
                CreatureDatabase.get(companion.creatureId)?.role == CreatureRole.LUCK
            }

        private fun hasLuckJoined(): Boolean =
            companions.any { companion ->
                CreatureDatabase.get(companion.creatureId)?.role == CreatureRole.LUCK
            }

        private fun maybeUseOutpostWarpForPreparation() {
            if (!warpUnlocked) return
            val shouldUseWarp = when (policy) {
                ProgressionPolicy.WarpSupport -> bossLosses > 0
                ProgressionPolicy.PrepFocused -> bossAttempts == 0 || bossLosses > 0
                else -> false
            }
            if (!shouldUseWarp) return
            warpUses += 2
            areaMoves += 2
            totalActions += 2
            gameMinutes += FACILITY_ACTION_MINUTES * 2
        }

        private fun shouldHealAfterBattle(): Boolean =
            activeIds.any { id ->
                val companion = companions.firstOrNull { it.instanceId == id } ?: return@any false
                val creature = CreatureDatabase.get(companion.creatureId) ?: return@any false
                companion.hp * 2 <= Leveling.maxHp(creature.hp, companion.level) || companion.mp <= 1
            }

        private fun healParty() {
            healUses += 1
            activeIds.forEach { id ->
                val index = companions.indexOfFirst { it.instanceId == id }
                if (index < 0) return@forEach
                val companion = companions[index]
                val creature = CreatureDatabase.get(companion.creatureId) ?: return@forEach
                companions[index] = companion.copy(
                    hp = Leveling.maxHp(creature.hp, companion.level),
                    mp = Leveling.maxMp(creature.mp, companion.level),
                )
            }
            gameMinutes += FACILITY_ACTION_MINUTES
            totalActions += 1
        }

        private fun trainForHours(hours: Int) {
            val capped = hours.coerceIn(0, TRAINING_MAX_HOURS)
            if (capped <= 0) return
            trainingUses += 1
            trainingHours += capped
            val exp = capped * TRAINING_EXP_PER_HOUR
            activeIds.take(MAX_TRAINING_SIZE).forEach { id ->
                val index = companions.indexOfFirst { it.instanceId == id }
                if (index >= 0) companions[index] = Leveling.grantExp(companions[index], exp).companion
            }
            gameMinutes += capped * 60.0
            totalActions += 1
            healParty()
        }

        private fun averageActiveLevel(): Double =
            activeIds.mapNotNull { id -> companions.firstOrNull { it.instanceId == id }?.level }.averageOrZero()

        private fun mapForNextExploration(): String {
            if (policy == ProgressionPolicy.CollectionFirst || policy == ProgressionPolicy.PrepFocused) {
                val missing = CreatureDatabase.t1Creatures.firstOrNull { it.id !in befriended }
                when (missing?.role) {
                    CreatureRole.ATCK, CreatureRole.DEFN -> return T1MapProgress.SETTLEMENT_OUTSKIRTS_MAP_ID
                    CreatureRole.AREA, CreatureRole.HEAL -> return T1MapProgress.STARGRASS_FORK_MAP_ID
                    CreatureRole.LUCK -> return T1MapProgress.DEEP_GATE_ROAD_MAP_ID
                    null -> Unit
                }
            }
            return when ((explorationSteps / 24) % 3) {
                0 -> T1MapProgress.SETTLEMENT_OUTSKIRTS_MAP_ID
                1 -> T1MapProgress.STARGRASS_FORK_MAP_ID
                else -> T1MapProgress.DEEP_GATE_ROAD_MAP_ID
            }
        }

        private fun estimatedBattleMinutes(turns: Int): Double =
            0.8 + turns * 0.55 + 1.2
    }

    private data class SimBattleOutcome(val state: BattleState, val turns: Int)
    private data class AutoBattleChoice(val instanceId: String, val skill: starsaga.data.SkillData)

    private const val EXPLORATION_ACTION_MINUTES = 0.08
    private const val FACILITY_ACTION_MINUTES = 0.25
}

private fun List<out Number>.averageOrZero(): Double =
    if (isEmpty()) 0.0 else sumOf { it.toDouble() } / size

private fun percentile(values: List<Int>, percentile: Double): Int {
    if (values.isEmpty()) return 0
    val index = ((values.size - 1) * percentile).roundToInt().coerceIn(values.indices)
    return values[index]
}
