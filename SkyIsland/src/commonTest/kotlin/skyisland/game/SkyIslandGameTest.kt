package skyisland.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import skyisland.data.Difficulty
import skyisland.data.Ids
import skyisland.data.InMemorySaveRepository
import skyisland.data.SaveData
import skyisland.data.SkillData

class SkyIslandGameTest {
    @Test
    fun tutorialStartsWithEscapeStoneAndDisablesAutoToggle() {
        val game = SkyIslandGame(InMemorySaveRepository(), Random(1))
        game.start()
        assertEquals(1, game.saveData.items[Ids.ITEM_ESCAPE_STONE])
        assertFalse(game.saveData.tutorialCompleted)
        val initial = game.saveData.autoMoveEnabled
        game.toggleAuto()
        assertEquals(initial, game.saveData.autoMoveEnabled)
    }

    @Test
    fun tutorialCanBeSkippedAndPersisted() {
        val repository = InMemorySaveRepository()
        val game = SkyIslandGame(repository, Random(1))
        game.start()

        game.skipTutorial()

        assertTrue(game.saveData.tutorialCompleted)
        assertTrue(game.saveData.autoMoveEnabled)
        assertEquals("チュートリアルをスキップしました", game.message)
        assertTrue(repository.load().tutorialCompleted)
    }

    @Test
    fun entranceGoalGuidanceIsShownOnceAfterTutorialCompletionAndPersisted() {
        val repository = InMemorySaveRepository()
        val game = SkyIslandGame(repository, Random(1))

        assertFalse(game.consumeEntranceGoalGuidance())
        game.skipTutorial()
        assertTrue(game.consumeEntranceGoalGuidance())
        assertFalse(game.consumeEntranceGoalGuidance())
        assertTrue(repository.load().entranceGoalGuidanceShown)
    }

    @Test
    fun craftWindSwordConsumesIdBasedRecipeAndEquipsIt() {
        val save = SaveData(
            tutorialCompleted = true,
            materials = mapOf(Ids.MAT_MIST_CRYSTAL to 3, Ids.MAT_WIND_FEATHER to 3),
        )
        val game = SkyIslandGame(InMemorySaveRepository(save), Random(1))
        assertTrue(game.craft(Ids.EQ_WIND_SWORD))
        assertEquals(Ids.EQ_WIND_SWORD, game.saveData.equippedWeaponId)
        assertEquals(0, game.saveData.materials[Ids.MAT_MIST_CRYSTAL])
        assertEquals(0, game.saveData.materials[Ids.MAT_WIND_FEATHER])
    }

    @Test
    fun craftActionIsDisabledWhenRequiredMaterialsAreMissing() {
        val game = SkyIslandGame(InMemorySaveRepository(SaveData(tutorialCompleted = true)), Random(1))

        assertFalse(game.canCraft(Ids.EQ_WIND_SWORD))
        assertFalse(game.craft(Ids.EQ_WIND_SWORD))
        assertTrue(game.saveData.ownedEquipments.isEmpty())
    }

    @Test
    fun ownedEquipmentCanBeEnhancedToThreeAndEquipped() {
        val save = SaveData(
            tutorialCompleted = true,
            materials = mapOf(Ids.MAT_MIST_CRYSTAL to 6),
            ownedEquipments = listOf(Ids.EQ_MIST_COAT),
        )
        val game = SkyIslandGame(InMemorySaveRepository(save), Random(1))

        assertTrue(game.canEnhance(Ids.EQ_MIST_COAT))
        assertTrue(game.enhance(Ids.EQ_MIST_COAT))
        assertTrue(game.enhance(Ids.EQ_MIST_COAT))
        assertTrue(game.enhance(Ids.EQ_MIST_COAT))
        assertEquals(3, game.equipmentLevel(Ids.EQ_MIST_COAT))
        assertFalse(game.canEnhance(Ids.EQ_MIST_COAT))
        assertTrue(game.equip(Ids.EQ_MIST_COAT))
        assertEquals(Ids.EQ_MIST_COAT, game.saveData.equippedArmorId)
    }

    @Test
    fun floorOneHasTwoByTwoChunksAndKasumi() {
        val game = SkyIslandGame(InMemorySaveRepository(SaveData(tutorialCompleted = true)), Random(2))
        game.start(Difficulty.ADVENTURE)
        assertEquals(20 * 20, game.dungeon!!.tiles.size)
        assertTrue(game.dungeon!!.enemies.any { it.enemyId == Ids.BOSS_KASUMI })
    }

    @Test
    fun reachingExitShowsClearResultEvenWhenBossStateWasNotUpdated() {
        val game = SkyIslandGame(InMemorySaveRepository(SaveData(tutorialCompleted = true)), Random(2))
        game.start()
        game.dungeon!!.enemies.clear()
        game.player.cell = Cell(DungeonGenerator.WIDTH - 3, DungeonGenerator.HEIGHT - 2)

        assertTrue(game.move(1, 0))
        assertEquals(RunEnd.CLEAR, game.result?.reason)
    }

    @Test
    fun kasumiFogLastsUntilNextEnemyTurnAndBossChunkStopsForcedExitTimer() {
        val game = SkyIslandGame(InMemorySaveRepository(SaveData(tutorialCompleted = true)), Random(2))
        game.start()
        val kasumi = game.dungeon!!.enemies.single { it.enemyId == Ids.BOSS_KASUMI }
        game.dungeon!!.enemies.removeAll { it !== kasumi }
        kasumi.cell = Cell(15, 15)
        kasumi.turn = 2
        game.player.cell = Cell(10, 10)

        assertTrue(game.move(1, 0))
        assertEquals(1, game.dungeon!!.bossFogTurns)
        assertEquals(0, game.chunkTurns)
        assertTrue(game.storyText.contains("大きな影が現れた"))
    }

    @Test
    fun deathKeepsSeventyPercentOfRunMaterialsAndOwnedEquipment() {
        val equipment = Ids.EQ_MIST_COAT
        val game = SkyIslandGame(
            InMemorySaveRepository(SaveData(tutorialCompleted = true, ownedEquipments = listOf(equipment))),
            Random(2),
        )
        game.start()
        game.dungeon!!.enemies.clear()
        game.dungeon!!.enemies += Enemy(99, Ids.ENEMY_CLOUD_SLIME, Cell(2, 1), 999)
        game.stats.materials[Ids.MAT_MIST_CRYSTAL] = 10
        game.player.hp = 1

        assertTrue(game.autoStep())
        assertEquals(RunEnd.DEATH, game.result?.reason)
        assertEquals(7, game.saveData.materials[Ids.MAT_MIST_CRYSTAL])
        assertTrue(equipment in game.saveData.ownedEquipments)
        assertEquals("次はもっと上手くいきます", game.result?.message)
    }

    @Test
    fun playerCanReturnToBaseAfterDeathAndRestartAtMaxHp() {
        val game = SkyIslandGame(InMemorySaveRepository(SaveData(tutorialCompleted = true)), Random(2))
        game.start()
        game.dungeon!!.enemies.clear()
        game.dungeon!!.enemies += Enemy(99, Ids.ENEMY_CLOUD_SLIME, Cell(2, 1), 999)
        game.player.hp = 1

        assertTrue(game.autoStep())
        assertEquals(RunEnd.DEATH, game.result?.reason)
        game.returnToBase()
        assertEquals(null, game.dungeon)
        game.start(Difficulty.ADVENTURE)
        assertEquals(game.maxHp, game.player.hp)
        assertEquals(null, game.result)
    }

    @Test
    fun autoStepIgnoresUnknownEnemyIdsWithoutCrashing() {
        val game = SkyIslandGame(InMemorySaveRepository(SaveData(tutorialCompleted = true)), Random(2))
        game.start()
        game.dungeon!!.enemies.clear()
        game.dungeon!!.enemies += Enemy(99, "UNKNOWN_ENEMY", Cell(2, 1), 10)

        assertTrue(game.autoStep())
        assertEquals(null, game.result)
    }

    @Test
    fun autoStepReturnsFalseWhenNoReachableTargetExists() {
        val game = SkyIslandGame(InMemorySaveRepository(SaveData(tutorialCompleted = true)), Random(2))
        game.start()
        game.dungeon!!.enemies.clear()
        game.dungeon!!.groundItems.clear()
        game.player.cell.neighbors().forEach { game.dungeon!!.tiles[it] = Tile.WALL }

        assertFalse(game.autoStep())
        assertEquals(null, game.result)
    }

    @Test
    fun autoStepFallsBackWhenNearestEnemyHasNoReachableAdjacentCell() {
        val game = SkyIslandGame(InMemorySaveRepository(SaveData(tutorialCompleted = true)), Random(2))
        game.start()
        game.dungeon!!.groundItems.clear()
        game.dungeon!!.enemies.clear()
        game.dungeon!!.enemies += Enemy(98, Ids.ENEMY_CLOUD_SLIME, Cell(4, 1), 999)
        game.dungeon!!.enemies += Enemy(99, Ids.ENEMY_CLOUD_SLIME, Cell(1, 4), 999)
        listOf(Cell(3, 1), Cell(5, 1), Cell(3, 2), Cell(4, 3), Cell(5, 2)).forEach {
            game.dungeon!!.tiles[it] = Tile.WALL
        }

        assertTrue(game.autoStep())
        assertEquals(Cell(1, 2), game.player.cell)
    }

    @Test
    fun actionLogRecordsBattleEventsWithTurnPrefix() {
        val game = SkyIslandGame(InMemorySaveRepository(SaveData(tutorialCompleted = true)), Random(2))
        game.start()
        game.dungeon!!.enemies.clear()
        game.dungeon!!.enemies += Enemy(99, Ids.ENEMY_CLOUD_SLIME, Cell(2, 1), 1)

        assertTrue(game.autoStep())
        val log = game.actionLog.toList()
        assertTrue(log.any { it.contains("雲スライムを倒した") }, "Log should contain defeat entry")
        assertTrue(log.any { it.contains("霧の結晶を入手") }, "Log should contain item entry")
        assertTrue(log.all { it.matches(Regex("\\[T\\d+\\] .+")) }, "All entries must have turn prefix")
        assertTrue(log.size <= 20, "Log must not exceed 20 entries")
    }

    @Test
    fun battleQueuesFlashEventsForEnemyAndPlayerCells() {
        val game = SkyIslandGame(InMemorySaveRepository(SaveData(tutorialCompleted = true)), Random(2))
        game.start()
        game.dungeon!!.enemies.clear()
        game.dungeon!!.enemies += Enemy(99, Ids.ENEMY_CLOUD_SLIME, Cell(2, 1), 999)

        assertTrue(game.autoStep())
        assertEquals(
            listOf(
                SkyIslandGame.DamageEvent(Cell(2, 1), isPlayer = false),
                SkyIslandGame.DamageEvent(Cell(1, 1), isPlayer = true),
            ),
            game.pendingFlash.toList(),
        )
    }

    @Test
    fun tutorialStoryBlocksMovementForTwoSeconds() {
        val game = SkyIslandGame(InMemorySaveRepository(), Random(1))
        game.start()

        assertTrue(game.isStoryVisible)
        assertFalse(game.move(1, 0))
        game.tickStory(2.0)
        assertFalse(game.isStoryVisible)
        assertTrue(game.move(1, 0))
    }

    @Test
    fun maxLevelSkillCrystallizesAndCanBeRelearnedWithPermanentBoost() {
        val save = SaveData(
            skills = listOf(SkillData(Ids.SKILL_MIST_HEAL, SkyIslandGame.MAX_SKILL_PROFICIENCY)),
        )
        val game = SkyIslandGame(InMemorySaveRepository(save), AlwaysZeroRandom)

        assertTrue(game.useSkill(Ids.SKILL_MIST_HEAL))
        assertTrue(game.saveData.skills.none { it.skillId == Ids.SKILL_MIST_HEAL })
        assertEquals(2, game.saveData.items[Ids.ITEM_SKILL_CRYSTAL])
        assertEquals(1, game.crystallizationEffectCount)

        assertTrue(game.relearnSkill(Ids.SKILL_MIST_HEAL))
        assertEquals(0, game.saveData.items[Ids.ITEM_SKILL_CRYSTAL])
        assertEquals(1, game.saveData.skillEnhancements[Ids.SKILL_MIST_HEAL])
        assertEquals(0, game.saveData.skills.single().proficiency)

        game.player.hp = 1
        assertTrue(game.useSkill(Ids.SKILL_MIST_HEAL))
        assertEquals(28, game.player.hp)
    }

    private object AlwaysZeroRandom : Random() {
        override fun nextBits(bitCount: Int) = 0
    }
}
