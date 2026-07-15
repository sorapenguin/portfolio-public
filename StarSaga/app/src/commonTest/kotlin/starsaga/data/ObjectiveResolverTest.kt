package starsaga.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import starsaga.map.T1Area

class ObjectiveResolverTest {
    @Test
    fun firstTownStartGuidesToEastRoad() {
        val objective = resolve(
            areaId = T1Area.FIRST_TOWN.id,
            ownedIds = emptySet(),
        )

        assertEquals("東の街道から草地へ向かおう", objective.main)
        assertEquals("集落近郊で最初のスターを探そう", objective.hint)
    }

    @Test
    fun zeroOwnedOutsideTownGuidesFirstStarSearch() {
        val objective = resolve(
            areaId = T1Area.SETTLEMENT_OUTSKIRTS.id,
            ownedIds = emptySet(),
        )

        assertEquals("集落近郊で最初のスターを探そう", objective.main)
    }

    @Test
    fun partialCollectionShowsCountAndHabitatHint() {
        val objective = resolve(
            areaId = T1Area.SETTLEMENT_OUTSKIRTS.id,
            ownedIds = setOf(1, 2),
        )

        assertEquals("T1スターを仲間にしよう 2/5", objective.main)
        assertEquals("星草の分かれ道で見つかりやすい", objective.hint)
    }

    @Test
    fun roleHabitatHintsMatchAreaWeights() {
        assertEquals(T1Area.SETTLEMENT_OUTSKIRTS.id, ObjectiveResolver.habitatHintForRole(CreatureRole.ATCK)?.areaId)
        assertEquals(T1Area.SETTLEMENT_OUTSKIRTS.id, ObjectiveResolver.habitatHintForRole(CreatureRole.DEFN)?.areaId)
        assertEquals(T1Area.STARGRASS_FORK.id, ObjectiveResolver.habitatHintForRole(CreatureRole.AREA)?.areaId)
        assertEquals(T1Area.STARGRASS_FORK.id, ObjectiveResolver.habitatHintForRole(CreatureRole.HEAL)?.areaId)
        assertEquals(T1Area.DEEP_GATE_ROAD.id, ObjectiveResolver.habitatHintForRole(CreatureRole.LUCK)?.areaId)
    }

    @Test
    fun individualMissingRolesReturnExpectedHint() {
        assertEquals("集落近郊で見つかりやすい", ObjectiveResolver.nextHabitatHint(setOf(2, 3, 4, 5))?.message)
        assertEquals("集落近郊で見つかりやすい", ObjectiveResolver.nextHabitatHint(setOf(1, 3, 4, 5))?.message)
        assertEquals("星草の分かれ道で見つかりやすい", ObjectiveResolver.nextHabitatHint(setOf(1, 2, 4, 5))?.message)
        assertEquals("星草の分かれ道で見つかりやすい", ObjectiveResolver.nextHabitatHint(setOf(1, 2, 3, 5))?.message)
        assertEquals("深門への道で見つかりやすい", ObjectiveResolver.nextHabitatHint(setOf(1, 2, 3, 4))?.message)
    }

    @Test
    fun fullCollectionHasNoHabitatHint() {
        assertNull(ObjectiveResolver.nextHabitatHint(setOf(1, 2, 3, 4, 5)))
    }

    @Test
    fun fiveOwnedBeforeOutpostGuidesOutpost() {
        val objective = resolve(
            areaId = T1Area.DEEP_GATE_ROAD.id,
            ownedIds = setOf(1, 2, 3, 4, 5),
            reachedOutpost = false,
        )

        assertEquals("深門への道を東へ進もう", objective.main)
    }

    @Test
    fun reachedOutpostBeforeWarpGuidesStarGate() {
        val objective = resolve(
            areaId = T1Area.T1_OUTPOST.id,
            ownedIds = setOf(1, 2, 3, 4, 5),
            reachedOutpost = true,
            warpUnlocked = false,
        )

        assertEquals("前哨地の星門を調べよう", objective.main)
        assertEquals("DeepGateにも挑戦できます", objective.hint)
    }

    @Test
    fun warpUnlockedBeforeBossGuidesDeepGate() {
        val objective = resolve(
            areaId = T1Area.T1_OUTPOST.id,
            ownedIds = setOf(1, 2, 3, 4, 5),
            reachedOutpost = true,
            warpUnlocked = true,
        )

        assertEquals("DeepGateから星草の主に挑もう", objective.main)
    }

    @Test
    fun bossClearedShowsPlanetClear() {
        val objective = resolve(
            areaId = T1Area.T1_OUTPOST.id,
            ownedIds = setOf(1, 2, 3, 4, 5),
            reachedOutpost = true,
            warpUnlocked = true,
            bossCleared = true,
        )

        assertEquals("第1惑星クリア", objective.main)
    }

    @Test
    fun unknownAreaDoesNotThrow() {
        val objective = resolve(
            areaId = "unknown",
            ownedIds = setOf(1),
        )

        assertFalse(objective.main.isBlank())
    }

    private fun resolve(
        areaId: String,
        ownedIds: Set<Int>,
        bossCleared: Boolean = false,
        reachedOutpost: Boolean = false,
        warpUnlocked: Boolean = false,
    ): T1Objective =
        ObjectiveResolver.resolveT1(
            T1ObjectiveContext(
                currentAreaId = areaId,
                befriendedCreatureIds = ownedIds,
                t1BossCleared = bossCleared,
                reachedT1Outpost = reachedOutpost,
                t1OutpostWarpUnlocked = warpUnlocked,
            ),
        )
}
