package skyisland.game

import kotlin.math.ceil
import kotlin.random.Random
import skyisland.data.*
import skyisland.data.BalanceLogRepository
import skyisland.data.NoOpBalanceLogRepository

class SkyIslandGame(
    private val saveRepository: SaveRepository,
    private val random: Random = Random.Default,
    private val balanceLog: BalanceLogRepository = NoOpBalanceLogRepository(),
) {
    data class DamageEvent(val cell: Cell, val isPlayer: Boolean)

    var saveData = saveRepository.load()
        private set
    var dungeon: Dungeon? = null
        private set
    var player = Player()
        private set
    var stats = RunStats()
        private set
    var tutorialStep: TutorialStep? = null
        private set
    var chunkTurns = 0
        private set
    var message = ""
        private set
    var result: RunResult? = null
        private set
    var crystallizationEffectCount = 0
        private set
    var turnCount = 0
        private set
    val actionLog: ArrayDeque<String> = ArrayDeque(20)
    private var sessionId = 0L
    private val sessionLines = mutableListOf<String>()
    val pendingFlash: ArrayDeque<DamageEvent> = ArrayDeque()
    var storyText = ""
        private set
    var storySecondsRemaining = 0.0
        private set
    private var lastChunk = Chunk(0, 0)
    private var tutorialMoves = 0
    private var bossIntroShown = false

    init {
        recoverStamina()
    }

    val maxHp: Int get() = 50 + (saveData.playerLevel - 1) * 5 + equipment()?.hp.orZero()
    val attack: Int get() = 8 + saveData.playerLevel + equipment(EquipmentSlot.WEAPON)?.let { it.attack + equipmentLevel(it.id) }.orZero()
    val defense: Int get() = 2 + equipment(EquipmentSlot.ARMOR)?.let { it.defense + equipmentLevel(it.id) }.orZero()
    val isStoryVisible: Boolean get() = storySecondsRemaining > 0.0

    fun start(difficulty: Difficulty = Difficulty.RELAXED, floorId: String = Ids.FLOOR_01) {
        val tutorial = !saveData.tutorialCompleted
        val actualFloorId = if (tutorial) Ids.FLOOR_01 else floorId
        if (actualFloorId == Ids.FLOOR_02 && !saveData.floor1Cleared) {
            message = "フロア2はフロア1クリア後に挑戦できます"
            return
        }
        if (actualFloorId == Ids.FLOOR_03 && !saveData.floor2Cleared) {
            message = "フロア3はフロア2クリア後に挑戦できます"
            return
        }
        val staminaCost = if (actualFloorId == Ids.FLOOR_02 || actualFloorId == Ids.FLOOR_03) 1 else 0
        if (saveData.stamina < staminaCost) {
            message = "スタミナが足りません"
            return
        }
        if (staminaCost > 0) saveData = saveData.copy(stamina = saveData.stamina - staminaCost)
        if (tutorial && saveData.items.getOrElse(Ids.ITEM_ESCAPE_STONE) { 0 } == 0) {
            saveData = saveData.copy(items = saveData.items.add(Ids.ITEM_ESCAPE_STONE, 1))
        }
        if (tutorial) saveData = saveData.copy(autoMoveEnabled = false)
        dungeon = DungeonGenerator(random).generate(difficulty, actualFloorId, tutorial)
        player = Player(hp = maxHp)
        stats = RunStats()
        actionLog.clear()
        pendingFlash.clear()
        result = null
        chunkTurns = 0
        turnCount = 0
        sessionId = currentEpochSeconds()
        sessionLines.clear()
        logBalance(0, "SESSION_START", "${difficulty.name}|floor:$actualFloorId|hp:${maxHp}|atk:${attack}|def:${defense}|lv:${saveData.playerLevel}|stamina:${saveData.stamina}")
        lastChunk = Chunk(0, 0)
        tutorialMoves = 0
        bossIntroShown = false
        tutorialStep = if (tutorial) TutorialStep.MOVE else null
        message = when {
            tutorial -> "タップした方向に動けます"
            actualFloorId == Ids.FLOOR_03 -> "光の島へ踏み込みました"
            actualFloorId == Ids.FLOOR_02 -> "嵐の島へ踏み込みました"
            else -> "霧に閉ざされた空島へ踏み込みました"
        }
        if (tutorial) {
            showStory("霧に閉ざされた空島の向こうに、何かがある。\n飛行石を握りしめ、島へ飛び込んだ", 2.0)
        }
        persist()
    }

    fun startDungeon(difficulty: Difficulty = Difficulty.RELAXED, floorId: String = Ids.FLOOR_01) = start(difficulty, floorId)

    fun move(dx: Int, dy: Int): Boolean {
        val dungeon = dungeon ?: return false
        if (result != null || isStoryVisible) return false
        val next = Cell(player.cell.x + dx, player.cell.y + dy)
        if (!canWalk(next) || dungeon.enemies.any { it.hp > 0 && it.cell == next }) return false
        if (dx != 0 || dy != 0) {
            player.lastDx = dx
            player.lastDy = dy
        }
        player.cell = next
        onPlayerAction()
        if (tutorialStep == TutorialStep.MOVE && ++tutorialMoves >= 2) {
            tutorialStep = TutorialStep.DEFEAT_SLIME
            message = "雲スライムに近づくと自動で攻撃します"
        }
        adjacentEnemy()?.let { basicAttack(it) }
        pickup()
        openChest()
        if (dungeon.tiles[player.cell] == Tile.EXIT) finish(RunEnd.CLEAR)
        return true
    }

    fun useSkill(skillId: String): Boolean {
        if (result != null || isStoryVisible) return false
        val skill = saveData.skills.firstOrNull { it.skillId == skillId } ?: return false
        val def = GameTables.skills[skillId] ?: return false
        val power = skillPower(def)
        val hpBefore = player.hp
        when (def.effect) {
            SkillEffect.FRONT_ATTACK -> adjacentEnemy()?.let { damage(it, attack + power) } ?: return false
            SkillEffect.HEAL -> player.hp = minOf(maxHp, player.hp + maxHp * power / 100)
            SkillEffect.SHIELD -> player.shieldTurns = power
            SkillEffect.AREA_ATTACK -> dungeon?.enemies?.filter { it.hp > 0 && it.cell.distance(player.cell) <= 2 }?.forEach { damage(it, attack + power) }
            SkillEffect.KNOCKBACK -> knockbackAdjacentEnemy() ?: return false
            SkillEffect.STUN -> stunAdjacentEnemy() ?: return false
            SkillEffect.MULTI_HIT -> multiHit(power)
            SkillEffect.LINE_ATTACK -> lineAttack(power)
            SkillEffect.ALL_ATTACK -> allAttack(power)
            SkillEffect.INVINCIBLE -> {
                player.invincibleTurns = power
                pushLog("浮遊で${power}ターンダメージを無効化！")
            }
        }
        logBalance(turnCount, "SKILL_USE", "${def.name}|${def.effect.name}|power:$power|prof:${skill.proficiency}|php:$hpBefore→${player.hp}/$maxHp")
        val wasMaxLevel = skill.proficiency >= MAX_SKILL_PROFICIENCY
        val proficiency = minOf(MAX_SKILL_PROFICIENCY, skill.proficiency + 1)
        saveData = saveData.copy(skills = saveData.skills.map {
            if (it.skillId == skillId) it.copy(proficiency = proficiency) else it
        })
        val crystallized = wasMaxLevel && tryCrystallize(def)
        if (skillId == Ids.SKILL_WIND_BLADE && tutorialStep == TutorialStep.USE_WIND_BLADE) {
            tutorialStep = TutorialStep.REVEAL_FOG
            message = "霧が晴れると新しいエリアが見えます"
        } else if (crystallized) {
            message = "${def.name}が結晶化しました！欠片を使うとより強く再習得できます"
        } else if (!wasMaxLevel && proficiency == MAX_SKILL_PROFICIENCY) {
            message = "${def.name} Lv3 Max！再習得で強化可能！"
        } else {
            message = "${def.name}を使いました"
        }
        onPlayerAction()
        return true
    }

    private fun knockbackAdjacentEnemy(): Unit? {
        val dungeon = dungeon ?: return null
        val enemy = adjacentEnemy() ?: return null
        val def = GameTables.enemies[enemy.enemyId]
        val dx = enemy.cell.x - player.cell.x
        val dy = enemy.cell.y - player.cell.y
        repeat(2) {
            val next = Cell(enemy.cell.x + dx, enemy.cell.y + dy)
            val occupied = dungeon.enemies.any { it !== enemy && it.hp > 0 && it.cell == next }
            if (!canWalk(next) || occupied) return@repeat
            enemy.cell = next
        }
        pushLog("突風で${def?.name ?: enemy.enemyId}を吹き飛ばした！")
        return Unit
    }

    private fun stunAdjacentEnemy(): Unit? {
        val enemy = adjacentEnemy() ?: return null
        val def = GameTables.enemies[enemy.enemyId]
        enemy.stunTurns = 1
        pushLog("凍霧で${def?.name ?: enemy.enemyId}を行動不能にした！")
        return Unit
    }

    private fun multiHit(power: Int) {
        repeat(3) {
            val targets = dungeon?.enemies?.filter { it.hp > 0 }.orEmpty()
            val enemy = targets.randomOrNull(random) ?: return@repeat
            val name = GameTables.enemies[enemy.enemyId]?.name ?: enemy.enemyId
            pushLog("星屑が${name}に当たった！")
            damage(enemy, power)
        }
    }

    private fun lineAttack(power: Int) {
        val targets = dungeon?.enemies?.filter {
            it.hp > 0 && if (player.lastDy != 0) it.cell.y == player.cell.y else it.cell.x == player.cell.x
        }.orEmpty()
        targets.forEach { damage(it, power) }
        pushLog("落雷が一列を貫いた！${targets.size}体に当たった")
    }

    private fun allAttack(power: Int) {
        dungeon?.enemies?.filter { it.hp > 0 }?.forEach { damage(it, power) }
        pushLog("島揺れがすべての敵を揺らした！")
    }

    fun relearnSkill(skillId: String): Boolean {
        val def = GameTables.skills[skillId] ?: return false
        if (saveData.skills.any { it.skillId == skillId } || inventoryCount(Ids.ITEM_SKILL_CRYSTAL) < SKILL_CRYSTAL_COST) return false
        val updatedSkills = if (saveData.skills.size < SKILL_SLOT_COUNT) {
            saveData.skills + SkillData(skillId)
        } else {
            val replaceIndex = random.nextInt(SKILL_SLOT_COUNT)
            saveData.skills.take(SKILL_SLOT_COUNT).mapIndexed { index, skill ->
                if (index == replaceIndex) SkillData(skillId) else skill
            }
        }
        saveData = saveData.copy(
            items = saveData.items.add(Ids.ITEM_SKILL_CRYSTAL, -SKILL_CRYSTAL_COST),
            skills = updatedSkills,
            skillEnhancements = saveData.skillEnhancements.add(skillId, 1),
            skillCrystallizationAttempts = saveData.skillCrystallizationAttempts - skillId,
        )
        message = "${def.name}を再習得しました！効果が10%強化されました"
        persist()
        return true
    }

    fun autoStep(): Boolean {
        val dungeon = dungeon ?: return false
        if (!saveData.autoMoveEnabled || result != null || isStoryVisible) return false
        if (player.hp * 100 <= maxHp * 30 && useItem(Ids.ITEM_HEAL_HERB)) return true
        if (player.hp * 100 <= maxHp * 10 && inventoryCount(Ids.ITEM_HEAL_HERB) == 0 && useItem(Ids.ITEM_ESCAPE_STONE)) {
            message = "HPが低いので脱出します"
            return true
        }
        adjacentEnemy()?.let {
            message = "近くの敵に向かいます"
            basicAttack(it)
            onPlayerAction()
            return true
        }
        val occupied = dungeon.enemies.filter { it.hp > 0 }.map { it.cell }.toSet()
        val targets = buildList {
            dungeon.groundItems.sortedBy { it.cell.distance(player.cell) }.forEach {
                add(AutoTarget(it.cell, "回復草を取りに行きます"))
            }
            dungeon.enemies.filter { it.hp > 0 }.sortedBy { it.cell.distance(player.cell) }.forEach {
                add(AutoTarget(it.cell, "近くの敵に向かいます", approachAdjacent = true))
            }
            dungeon.tiles.entries.firstOrNull { it.value == Tile.EXIT }?.let {
                add(AutoTarget(it.key, "出口へ向かいます"))
            }
        }
        for (target in targets) {
            val destinations = if (target.approachAdjacent) target.cell.neighbors() else listOf(target.cell)
            for (destination in destinations.filter { canWalk(it) && it !in occupied }.sortedBy { it.distance(player.cell) }) {
                val next = PathFinder.find(player.cell, destination) { canWalk(it) && it !in occupied }.firstOrNull() ?: continue
                message = target.message
                return move(next.x - player.cell.x, next.y - player.cell.y)
            }
        }
        return false
    }

    fun useItem(itemId: String): Boolean {
        if (inventoryCount(itemId) <= 0 || result != null) return false
        val hpBefore = player.hp
        when (itemId) {
            Ids.ITEM_HEAL_HERB -> player.hp = minOf(maxHp, player.hp + maxHp / 2)
            Ids.ITEM_ESCAPE_STONE -> finish(RunEnd.ESCAPE)
            else -> return false
        }
        logBalance(turnCount, "ITEM_USE", "${GameTables.items[itemId]?.name ?: itemId}|php:$hpBefore→${player.hp}/$maxHp")
        saveData = saveData.copy(items = saveData.items.add(itemId, -1))
        persist()
        return true
    }

    fun craft(equipmentId: String): Boolean {
        val def = GameTables.equipments[equipmentId] ?: return false
        if (equipmentId in saveData.ownedEquipments || !hasMaterials(def.recipe)) return false
        saveData = saveData.copy(
            materials = saveData.materials.subtract(def.recipe),
            ownedEquipments = saveData.ownedEquipments + equipmentId,
        )
        equip(equipmentId)
        persist()
        return true
    }

    fun enhance(equipmentId: String): Boolean {
        if (equipmentId !in saveData.ownedEquipments) return false
        val level = saveData.equipmentLevels.getOrElse(equipmentId) { 0 }
        val cost = level + 1
        if (level >= 3 || saveData.materials.getOrElse(Ids.MAT_MIST_CRYSTAL) { 0 } < cost) return false
        saveData = saveData.copy(
            materials = saveData.materials.add(Ids.MAT_MIST_CRYSTAL, -cost),
            equipmentLevels = saveData.equipmentLevels + (equipmentId to level + 1),
        )
        persist()
        return true
    }

    fun equip(equipmentId: String): Boolean {
        val def = GameTables.equipments[equipmentId] ?: return false
        if (equipmentId !in saveData.ownedEquipments) return false
        saveData = when (def.slot) {
            EquipmentSlot.WEAPON -> saveData.copy(equippedWeaponId = equipmentId)
            EquipmentSlot.ARMOR -> saveData.copy(equippedArmorId = equipmentId)
        }
        persist()
        return true
    }

    fun completeTutorial() {
        saveData = saveData.copy(tutorialCompleted = true, autoMoveEnabled = true)
        tutorialStep = TutorialStep.COMPLETE
        message = "セミオートモードを使うと自動で進みます（推奨）"
        persist()
    }

    fun skipTutorial() {
        if (saveData.tutorialCompleted) return
        saveData = saveData.copy(tutorialCompleted = true, autoMoveEnabled = true)
        tutorialStep = TutorialStep.COMPLETE
        storyText = ""
        storySecondsRemaining = 0.0
        message = "チュートリアルをスキップしました"
        persist()
    }

    fun toggleAuto() {
        if (saveData.tutorialCompleted) {
            saveData = saveData.copy(autoMoveEnabled = !saveData.autoMoveEnabled)
            persist()
        }
    }

    fun consumeEntranceGoalGuidance(): Boolean {
        if (!saveData.tutorialCompleted || saveData.entranceGoalGuidanceShown) return false
        saveData = saveData.copy(entranceGoalGuidanceShown = true)
        persist()
        return true
    }

    fun returnToBase() {
        if (result == null) return
        dungeon = null
        result = null
        player = Player(hp = maxHp)
        chunkTurns = 0
        message = "拠点に戻りました"
    }

    fun escape() {
        if (dungeon == null || result != null) return
        finish(RunEnd.ESCAPE)
    }

    fun tickStory(elapsedSeconds: Double) {
        if (storySecondsRemaining <= 0.0) return
        storySecondsRemaining = maxOf(0.0, storySecondsRemaining - elapsedSeconds)
        if (storySecondsRemaining == 0.0) storyText = ""
    }

    fun canCraft(equipmentId: String): Boolean {
        val def = GameTables.equipments[equipmentId] ?: return false
        return equipmentId !in saveData.ownedEquipments && hasMaterials(def.recipe)
    }

    fun canEnhance(equipmentId: String): Boolean {
        if (equipmentId !in saveData.ownedEquipments) return false
        val level = equipmentLevel(equipmentId)
        return level < 3 && saveData.materials.getOrElse(Ids.MAT_MIST_CRYSTAL) { 0 } >= level + 1
    }

    fun bossStrength(floorId: String = dungeon?.floorId ?: Ids.FLOOR_01): String {
        val bossId = when (floorId) {
            Ids.FLOOR_03 -> Ids.BOSS_LUMEN
            Ids.FLOOR_02 -> Ids.BOSS_TEMPE
            else -> Ids.BOSS_KASUMI
        }
        val boss = GameTables.enemies[bossId] ?: return "かなり危険"
        return when (ceil(boss.maxHp.toDouble() / attack).toInt()) {
            in 0..5 -> "余裕"
            in 6..7 -> "互角"
            in 8..10 -> "危険"
            else -> "かなり危険"
        }
    }

    private fun basicAttack(enemy: Enemy) = damage(enemy, attack)

    private fun damage(enemy: Enemy, amount: Int) {
        val def = GameTables.enemies[enemy.enemyId] ?: return
        enemy.hp -= amount
        pendingFlash.addLast(DamageEvent(enemy.cell, isPlayer = false))
        pushLog("${def.name}に${amount}ダメージ")
        logBalance(turnCount, "ATTACK", "${def.name}|dmg:$amount|ehp:${maxOf(0, enemy.hp)}/${def.maxHp}|php:${player.hp}/$maxHp")
        if (enemy.hp <= 0) {
            pushLog("${def.name}を倒した")
            stats.defeatedEnemies++
            gainExp(def.exp)
            val boss = enemy.enemyId == Ids.BOSS_KASUMI || enemy.enemyId == Ids.BOSS_TEMPE || enemy.enemyId == Ids.BOSS_LUMEN
            drops(enemy.enemyId)
            if (boss) {
                dungeon?.bossDefeated = true
                message = when (enemy.enemyId) {
                    Ids.BOSS_LUMEN -> "霧の核が砕けた。空島に光が戻ってくる"
                    Ids.BOSS_TEMPE -> "嵐の中心が砕けた。霧の核はあの先だ"
                    else -> "霧が少し晴れた。奥の島が見えてくる"
                }
            }
            if (tutorialStep == TutorialStep.DEFEAT_SLIME && enemy.enemyId == Ids.ENEMY_CLOUD_SLIME) {
                tutorialStep = TutorialStep.PICK_HERB
                message = "回復草を拾えます"
            }
        }
    }

    private fun drops(enemyId: String) {
        when (enemyId) {
            Ids.ENEMY_CLOUD_SLIME -> chance(0.70) { gainMaterial(Ids.MAT_MIST_CRYSTAL) }
            Ids.ENEMY_WIND_BIRD -> {
                chance(0.60) { gainMaterial(Ids.MAT_WIND_FEATHER) }
                chance(0.30) { gainMaterial(Ids.MAT_MIST_CRYSTAL) }
            }
            Ids.ENEMY_THUNDER_BUG -> chance(0.70) { gainMaterial(Ids.MAT_THUNDER_SHARD) }
            Ids.ENEMY_MIST_JELLYFISH -> chance(0.60) { gainMaterial(Ids.MAT_MIST_CRYSTAL) }
            Ids.ENEMY_STONE_GOLEM -> chance(0.70) { gainMaterial(Ids.MAT_CLOUD_CORE) }
            Ids.BOSS_KASUMI -> {
                gainMaterial(Ids.MAT_MIST_CRYSTAL, 5)
                gainMaterial(Ids.MAT_WIND_FEATHER, 2)
                if (!saveData.floor1Cleared) {
                    gainMaterial(Ids.MAT_MIST_CRYSTAL, 10)
                    gainMaterial(Ids.MAT_WIND_FEATHER, 4)
                }
                chance(0.15) {
                    val equipment = if (random.nextBoolean()) Ids.EQ_MIST_COAT else Ids.EQ_MIST_AMULET
                    if (equipment !in saveData.ownedEquipments) saveData = saveData.copy(ownedEquipments = saveData.ownedEquipments + equipment)
                }
            }
            Ids.BOSS_TEMPE -> {
                gainMaterial(Ids.MAT_THUNDER_SHARD, if (dungeon?.difficulty == Difficulty.ADVENTURE) 4 else 3)
                gainMaterial(Ids.MAT_WIND_FEATHER, 3)
            }
            Ids.BOSS_LUMEN -> gainMaterial(Ids.MAT_STORM_CORE)
        }
    }

    private fun pickup() {
        val item = dungeon?.groundItems?.firstOrNull { it.cell == player.cell } ?: return
        dungeon?.groundItems?.remove(item)
        saveData = saveData.copy(items = saveData.items.add(item.itemId, 1))
        pushLog("${GameTables.items[item.itemId]?.name ?: item.itemId}を拾った")
        logBalance(turnCount, "ITEM_PICKUP", "${GameTables.items[item.itemId]?.name ?: item.itemId}|php:${player.hp}/$maxHp")
        if (tutorialStep == TutorialStep.PICK_HERB) {
            tutorialStep = TutorialStep.USE_WIND_BLADE
            message = "スキルをタップすると使えます"
        }
    }

    private fun openChest() {
        val dungeon = dungeon ?: return
        if (!dungeon.chests.remove(player.cell)) return
        gainMaterial(Ids.MAT_MIST_CRYSTAL, random.nextInt(2, 4))
        if (dungeon.floorId == Ids.FLOOR_02 || dungeon.floorId == Ids.FLOOR_03) {
            chance(0.30) { gainMaterial(Ids.MAT_STAR_SAND) }
        }
        chance(0.40) { saveData = saveData.copy(items = saveData.items.add(Ids.ITEM_HEAL_HERB, 1)) }
        chance(0.20) {
            val skill = chestSkillTable(dungeon.floorId).random(random)
            acquireSkill(skill)
        }
    }

    private fun chestSkillTable(floorId: String) = when (floorId) {
        Ids.FLOOR_02 -> listOf(Ids.SKILL_GUST, Ids.SKILL_FREEZE_MIST, Ids.SKILL_STARDUST)
        Ids.FLOOR_03 -> listOf(Ids.SKILL_LIGHTNING, Ids.SKILL_ISLAND_QUAKE, Ids.SKILL_LEVITATE)
        else -> listOf(Ids.SKILL_CLOUD_SHIELD, Ids.SKILL_THUNDER_CLOUD)
    }

    private fun acquireSkill(skillId: String) {
        if (saveData.skills.any { it.skillId == skillId }) return
        val def = GameTables.skills[skillId] ?: return
        val currentSkills = saveData.skills.take(SKILL_SLOT_COUNT)
        if (currentSkills.size < SKILL_SLOT_COUNT) {
            saveData = saveData.copy(skills = saveData.skills + SkillData(skillId))
            pushLog("${def.name}を習得した")
        } else {
            val replaceIndex = random.nextInt(SKILL_SLOT_COUNT)
            val replaced = currentSkills[replaceIndex]
            val updated = currentSkills.mapIndexed { index, skill ->
                if (index == replaceIndex) SkillData(skillId) else skill
            }
            val replacedName = GameTables.skills[replaced.skillId]?.name ?: replaced.skillId
            pushLog("${def.name}を習得し、${replacedName}と入れ替えた")
            saveData = saveData.copy(skills = updated)
        }
    }

    private fun onPlayerAction() {
        turnCount++
        val dungeon = dungeon ?: return
        val chunk = Chunk(player.cell.x / DungeonGenerator.CHUNK_SIZE, player.cell.y / DungeonGenerator.CHUNK_SIZE)
        if (chunk != lastChunk) {
            lastChunk = chunk
            chunkTurns = 0
            if (dungeon.visitedChunks.add(chunk)) stats.revealedChunks++
            logBalance(turnCount, "CHUNK_ENTER", "chunk:${chunk.x}_${chunk.y}|php:${player.hp}/$maxHp|atk:$attack|def:$defense")
            if (tutorialStep == TutorialStep.REVEAL_FOG) completeTutorial()
            if (isBossChunk(chunk) && !bossIntroShown) {
                bossIntroShown = true
                logBalance(turnCount, "BOSS_ENCOUNTER", "php:${player.hp}/$maxHp|atk:$attack|def:$defense|lv:${saveData.playerLevel}")
                val text = when (dungeon.floorId) {
                    Ids.FLOOR_03 -> "これが霧の根源か。終わらせる\n今の強さ: ${bossStrength()}"
                    Ids.FLOOR_02 -> "嵐が激しくなる。霧の中心が近い\n今の強さ: ${bossStrength()}"
                    else -> "大きな影が現れた。この島の守り人か…\n今の強さ: ${bossStrength()}"
                }
                showStory(text, 1.5)
            }
        } else if (!isBossChunk(chunk)) {
            chunkTurns++
            if (chunkTurns >= 120) {
                player.cell = Cell(1, 1)
                finish(RunEnd.FORCED_EXIT)
                return
            }
        }
        enemyTurn()
        when {
            chunkTurns >= 100 -> message = "飛行石が限界！急いで！"
            chunkTurns >= 80 -> message = "風が強くなってきた…"
        }
        if (player.shieldTurns > 0) player.shieldTurns--
        persist()
    }

    private fun enemyTurn() {
        val dungeon = dungeon ?: return
        if (dungeon.bossFogTurns > 0) dungeon.bossFogTurns--
        for (enemy in dungeon.enemies.filter { it.hp > 0 }.toList()) {
            val def = GameTables.enemies[enemy.enemyId] ?: continue
            val playerChunk = Chunk(player.cell.x / DungeonGenerator.CHUNK_SIZE, player.cell.y / DungeonGenerator.CHUNK_SIZE)
            if (def.behavior == EnemyBehavior.BOSS && !isBossChunk(playerChunk)) continue
            if (enemy.stunTurns > 0) {
                enemy.stunTurns--
                pushLog("${def.name}は行動できない")
                continue
            }
            enemy.turn++
            if (enemy.enemyId == Ids.BOSS_TEMPE) {
                tempeTurn(enemy, def)
                if (result != null) return
                continue
            }
            if (enemy.enemyId == Ids.BOSS_LUMEN) {
                lumenTurn(enemy, def)
                if (result != null) return
                continue
            }
            if (def.behavior == EnemyBehavior.STATIC) {
                if (enemy.cell.distance(player.cell) <= 2) {
                    pushLog("${def.name}の電撃！")
                    receiveDamage(def.attack)
                }
                if (result != null) return
                continue
            }
            if (enemy.enemyId == Ids.BOSS_KASUMI && enemy.turn % 3 == 0) {
                message = "カスミが霧を噴き出した"
                dungeon.bossFogTurns = 1
            }
            val steps = when {
                def.behavior == EnemyBehavior.RUSH -> 2
                def.behavior == EnemyBehavior.BOSS && enemy.hp * 2 <= def.maxHp -> 2
                else -> 1
            }
            repeat(steps) {
                if (result != null) return
                if (enemy.cell.distance(player.cell) == 1) {
                    receiveDamage(def.attack)
                    return@repeat
                }
                val path = PathFinder.find(enemy.cell, player.cell) { cell ->
                    canWalk(cell) && dungeon.enemies.none { it !== enemy && it.hp > 0 && it.cell == cell }
                }
                path.firstOrNull()?.let {
                    if (it == player.cell) receiveDamage(def.attack) else enemy.cell = it
                }
            }
        }
    }

    private fun tempeTurn(enemy: Enemy, def: EnemyDef) {
        val dungeon = dungeon ?: return
        val bossPhase2 = enemy.hp * 2 <= def.maxHp
        if (enemy.turn % 4 == 0) {
            val isVertical = random.nextBoolean()
            val lineCount = if (bossPhase2) 2 else 1
            val affectedCells = mutableSetOf<Cell>()
            val width = dungeonWidth(dungeon)
            val height = dungeonHeight(dungeon)
            repeat(lineCount) { offset ->
                if (isVertical) {
                    val col = random.nextInt(1, width - 1)
                    for (y in 0 until height) affectedCells += Cell(col + offset, y)
                } else {
                    val row = random.nextInt(1, height - 1)
                    for (x in 0 until width) affectedCells += Cell(x, row + offset)
                }
            }
            dungeon.lightningPreview = affectedCells
            if (player.cell in affectedCells) receiveDamage(def.attack)
            if (bossPhase2) pushLog("テンペが怒り狂っている！")
            pushLog(if (isVertical) "テンペが縦列に落雷した！" else "テンペが横列に落雷した！")
            return
        }

        dungeon.lightningPreview = emptySet()
        val steps = if (bossPhase2) 3 else 2
        repeat(steps) {
            if (result != null) return
            if (enemy.cell.distance(player.cell) == 1) {
                receiveDamage(def.attack)
                return@repeat
            }
            val occupied = dungeon.enemies.filter { it.hp > 0 && it !== enemy }.map { it.cell }.toSet()
            val next = enemy.cell.neighbors()
                .filter { canWalk(it) && it !in occupied }
                .minByOrNull { it.distance(player.cell) }
            if (next != null) enemy.cell = next
        }
        if (enemy.cell.distance(player.cell) == 1 && result == null) receiveDamage(def.attack)
    }

    private fun lumenTurn(enemy: Enemy, def: EnemyDef) {
        val dungeon = dungeon ?: return
        val previousPreview = dungeon.lightningPreview
        if (previousPreview.isNotEmpty()) {
            if (player.cell in previousPreview) receiveDamage(def.attack)
            dungeon.lightningPreview = emptySet()
            pushLog("ルーメンの光が炸裂した！")
            return
        }

        val phase = when {
            enemy.hp * 3 > def.maxHp * 2 -> 1
            enemy.hp * 3 > def.maxHp -> 2
            else -> 3
        }
        val attackInterval = if (phase == 3) 2 else 3
        if (enemy.turn % attackInterval == 0) {
            val preview = lumenPreview(enemy.cell, phase, dungeon)
            dungeon.lightningPreview = preview
            pushLog(if (phase == 3) "ルーメンの核が激しく脈動している！" else "ルーメンが光を溜めている…")
            return
        }

        val occupied = dungeon.enemies.filter { it.hp > 0 && it !== enemy }.map { it.cell }.toSet()
        val next = enemy.cell.neighbors()
            .filter { canWalk(it) && it !in occupied }
            .minByOrNull { it.distance(player.cell) }
        if (next != null) enemy.cell = next
        if (enemy.cell.distance(player.cell) == 1 && result == null) receiveDamage(def.attack)
    }

    private fun lumenPreview(center: Cell, phase: Int, dungeon: Dungeon): Set<Cell> {
        val square = (-2..2).flatMap { dx ->
            (-2..2).map { dy -> Cell(center.x + dx, center.y + dy) }
        }
        val rays = if (phase >= 2) {
            listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1, 1 to 1, -1 to 1).flatMap { (dx, dy) ->
                (1..4).map { step -> Cell(center.x + dx * step, center.y + dy * step) }
            }
        } else {
            emptyList()
        }
        return (square + rays).filter { dungeon.tiles[it] == Tile.FLOOR }.toSet()
    }

    private fun receiveDamage(raw: Int) {
        if (player.invincibleTurns > 0) {
            player.invincibleTurns--
            pushLog("浮遊でダメージを無効化した")
            logBalance(turnCount, "DAMAGE_BLOCKED", "raw:$raw|php:${player.hp}/$maxHp|invincible:${player.invincibleTurns}")
            return
        }
        val reduced = maxOf(1, raw - defense - if (player.shieldTurns > 0) 4 else 0)
        pushLog("${reduced}ダメージを受けた")
        pendingFlash.addLast(DamageEvent(player.cell, isPlayer = true))
        val hpBefore = player.hp
        player.hp -= ceil(reduced * (dungeon?.difficulty?.attackMultiplier ?: 1.0)).toInt()
        logBalance(turnCount, "DAMAGE_TAKEN", "raw:$raw|reduced:$reduced|php:${player.hp}/$maxHp|shield:${player.shieldTurns}")
        if (player.hp <= 0) finish(RunEnd.DEATH)
    }

    private fun finish(reason: RunEnd) {
        if (result != null) return
        val kept = if (reason == RunEnd.DEATH) stats.materials.mapValues { (_, amount) -> ceil(amount * 0.7).toInt() } else stats.materials.toMap()
        saveData = saveData.copy(
            materials = saveData.materials.merge(kept),
            floor1Cleared = saveData.floor1Cleared || (reason == RunEnd.CLEAR && dungeon?.floorId == Ids.FLOOR_01),
            floor2Cleared = saveData.floor2Cleared || (reason == RunEnd.CLEAR && dungeon?.floorId == Ids.FLOOR_02),
            floor3Cleared = saveData.floor3Cleared || (reason == RunEnd.CLEAR && dungeon?.floorId == Ids.FLOOR_03),
        )
        val closing = when (reason) {
            RunEnd.CLEAR -> when (dungeon?.floorId) {
                Ids.FLOOR_03 -> "霧の核を砕きました！"
                Ids.FLOOR_02 -> "嵐の島を突破しました！"
                else -> "霧が少し晴れました！また空島へ"
            }
            RunEnd.ESCAPE -> "いい探索でした！素材が集まっています"
            RunEnd.FORCED_EXIT -> "風が強かったですね。また挑戦しましょう"
            RunEnd.DEATH -> "次はもっと上手くいきます"
        }
        val hint = when {
            GameTables.equipments[Ids.EQ_WIND_SWORD]?.let { hasMaterials(it.recipe) } == true && Ids.EQ_WIND_SWORD !in saveData.ownedEquipments -> "風の剣を作れます！"
            saveData.playerLevel >= 2 -> "カスミに挑戦できるLvです！"
            else -> null
        }
        result = RunResult(reason, stats.revealedChunks, stats.defeatedEnemies, kept, closing, hint)
        message = if (reason == RunEnd.FORCED_EXIT) "風に押し戻された！\n$closing" else closing
        logBalance(turnCount, "SESSION_END", "${reason.name}|chunks:${stats.revealedChunks}|enemies:${stats.defeatedEnemies}|php:${player.hp}/$maxHp|lv:${saveData.playerLevel}")
        balanceLog.appendLines(sessionLines.toList())
        sessionLines.clear()
        persist()
    }

    private fun gainMaterial(id: String, amount: Int = 1) {
        stats.materials[id] = stats.materials.getOrElse(id) { 0 } + amount
        pushLog("${GameTables.materials[id]?.name ?: id}を入手")
    }
    private fun gainExp(amount: Int) {
        val exp = saveData.playerExp + amount
        saveData = saveData.copy(playerLevel = 1 + exp / 100, playerExp = exp)
    }
    private fun chance(base: Double, block: () -> Unit) {
        if (random.nextDouble() < minOf(1.0, base * (dungeon?.difficulty?.dropMultiplier ?: 1.0))) block()
    }
    fun adjacentEnemy() = dungeon?.enemies?.firstOrNull { it.hp > 0 && it.cell.distance(player.cell) == 1 }
    private fun inventoryCount(id: String) = saveData.items.getOrElse(id) { 0 }
    private fun canWalk(cell: Cell) = dungeon?.tiles?.get(cell)?.let { it != Tile.WALL } == true
    private fun isBossChunk(chunk: Chunk) = dungeon?.enemies?.any {
        (it.enemyId == Ids.BOSS_KASUMI || it.enemyId == Ids.BOSS_TEMPE || it.enemyId == Ids.BOSS_LUMEN) &&
            Chunk(it.cell.x / DungeonGenerator.CHUNK_SIZE, it.cell.y / DungeonGenerator.CHUNK_SIZE) == chunk
    } == true
    private fun dungeonWidth(dungeon: Dungeon) = dungeon.tiles.keys.maxOf { it.x } + 1
    private fun dungeonHeight(dungeon: Dungeon) = dungeon.tiles.keys.maxOf { it.y } + 1
    private fun hasMaterials(recipe: Map<String, Int>) = recipe.all { (id, required) -> saveData.materials.getOrElse(id) { 0 } >= required }
    private fun equipment(slot: EquipmentSlot? = null): EquipmentDef? {
        val id = when (slot) {
            EquipmentSlot.WEAPON -> saveData.equippedWeaponId
            EquipmentSlot.ARMOR -> saveData.equippedArmorId
            null -> saveData.equippedArmorId
        }
        return id?.let(GameTables.equipments::get)
    }
    fun equipmentLevel(id: String) = saveData.equipmentLevels.getOrElse(id) { 0 }
    private fun skillPower(def: SkillDef): Int {
        val enhancement = saveData.skillEnhancements.getOrElse(def.id) { 0 }
        return (def.power * (100 + enhancement * 10) + 99) / 100
    }
    private fun tryCrystallize(def: SkillDef): Boolean {
        val attempts = saveData.skillCrystallizationAttempts.getOrElse(def.id) { 0 }
        val chance = minOf(1.0, def.crystallizationBaseChance + attempts * def.crystallizationChanceStep)
        if (random.nextDouble() >= chance) {
            saveData = saveData.copy(skillCrystallizationAttempts = saveData.skillCrystallizationAttempts.add(def.id, 1))
            return false
        }
        saveData = saveData.copy(
            items = saveData.items.add(Ids.ITEM_SKILL_CRYSTAL, SKILL_CRYSTAL_COST),
            skills = saveData.skills.filterNot { it.skillId == def.id },
            skillCrystallizationAttempts = saveData.skillCrystallizationAttempts - def.id,
        )
        crystallizationEffectCount++
        return true
    }
    private fun showStory(text: String, seconds: Double) {
        storyText = text
        storySecondsRemaining = seconds
    }
    private fun pushLog(entry: String) {
        if (actionLog.size >= 20) actionLog.removeFirst()
        actionLog.addLast("[T$turnCount] $entry")
    }
    private fun logBalance(turn: Int, event: String, detail: String) {
        sessionLines += "${currentEpochSeconds()},$sessionId,T$turn,$event,$detail"
    }
    private fun persist() = saveRepository.save(saveData.copy(schemaVersion = SaveData().schemaVersion, lastSavedAt = currentEpochSeconds()))
    private fun recoverStamina() {
        val elapsed = currentEpochSeconds() - saveData.lastSavedAt
        val recovered = (elapsed / STAMINA_RECOVERY_SECONDS).toInt()
        if (recovered > 0 && saveData.stamina < MAX_STAMINA) {
            saveData = saveData.copy(stamina = minOf(MAX_STAMINA, saveData.stamina + recovered))
            persist()
        }
    }

    private data class AutoTarget(val cell: Cell, val message: String, val approachAdjacent: Boolean = false)

    fun debugResetStamina() {
        saveData = saveData.copy(stamina = MAX_STAMINA)
        persist()
    }

    companion object {
        const val MAX_SKILL_PROFICIENCY = 20
        const val SKILL_CRYSTAL_COST = 2
        const val SKILL_SLOT_COUNT = 4
        const val MAX_STAMINA = 8
        const val STAMINA_RECOVERY_SECONDS = 1800L
    }
}

private fun Int?.orZero() = this ?: 0
private fun Map<String, Int>.add(id: String, amount: Int) = this + (id to maxOf(0, getOrElse(id) { 0 } + amount))
private fun Map<String, Int>.subtract(cost: Map<String, Int>) = cost.entries.fold(this) { map, (id, amount) -> map.add(id, -amount) }
private fun Map<String, Int>.merge(other: Map<String, Int>) = other.entries.fold(this) { map, (id, amount) -> map.add(id, amount) }
expect fun currentEpochSeconds(): Long
