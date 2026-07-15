package starsaga.save

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import starsaga.data.RpgSaveData
import starsaga.map.SaveMigration
import starsaga.map.T1MapProgress

class SaveDataJsonCompatibilityTest {
    @Test
    fun missingT1ClearAcknowledgedDefaultsToFalse() {
        val encodedOldSave = """
            {
              "version": 1,
              "currentMapId": "planet_t1",
              "playerCol": 3,
              "playerRow": 3,
              "t1BossCleared": true
            }
        """.trimIndent()

        val decoded = SaveDataJson.decode(encodedOldSave)

        assertTrue(decoded.t1BossCleared)
        assertFalse(decoded.t1ClearAcknowledged)
    }

    @Test
    fun missingM1FieldsUseSafeDefaults() {
        val encodedOldSave = """
            {
              "version": 1,
              "currentMapId": "planet_t1",
              "playerCol": 3,
              "playerRow": 3
            }
        """.trimIndent()

        val decoded = SaveDataJson.decode(encodedOldSave)

        assertEquals(T1MapProgress.CURRENT_REVISION, decoded.t1MapRevision)
        assertEquals(T1MapProgress.DEFAULT_AREA_ID, decoded.currentT1AreaId)
        assertFalse(decoded.reachedT1Outpost)
        assertFalse(decoded.t1OutpostWarpUnlocked)
        assertFalse(decoded.debugUnlimitedHeal)
    }

    @Test
    fun newSaveUsesM1Defaults() {
        val save = RpgSaveData()

        assertEquals(T1MapProgress.CURRENT_REVISION, save.t1MapRevision)
        assertEquals(T1MapProgress.DEFAULT_AREA_ID, save.currentT1AreaId)
        assertFalse(save.reachedT1Outpost)
        assertFalse(save.t1OutpostWarpUnlocked)
        assertFalse(save.debugUnlimitedHeal)
    }

    @Test
    fun validCurrentPlanetCoordinateIsPreserved() {
        val save = RpgSaveData(currentMapId = T1MapProgress.FIRST_TOWN_MAP_ID, playerCol = 10, playerRow = 10)

        val migrated = SaveMigration.migrate(save)

        assertEquals(T1MapProgress.FIRST_TOWN_MAP_ID, migrated.currentMapId)
        assertEquals(10, migrated.playerCol)
        assertEquals(10, migrated.playerRow)
    }

    @Test
    fun validOutskirtsCoordinateIsPreserved() {
        val save = RpgSaveData(currentMapId = T1MapProgress.SETTLEMENT_OUTSKIRTS_MAP_ID, playerCol = 8, playerRow = 10)

        val migrated = SaveMigration.migrate(save)

        assertEquals(T1MapProgress.SETTLEMENT_OUTSKIRTS_MAP_ID, migrated.currentMapId)
        assertEquals(8, migrated.playerCol)
        assertEquals(10, migrated.playerRow)
    }

    @Test
    fun revisionOneLegacyPlanetCoordinateMovesToFirstTown() {
        val save = RpgSaveData(
            currentMapId = T1MapProgress.LEGACY_PLANET_MAP_ID,
            playerCol = 25,
            playerRow = 10,
            t1MapRevision = 1,
        )

        val migrated = SaveMigration.migrate(save)

        assertEquals(T1MapProgress.FIRST_TOWN_MAP_ID, migrated.currentMapId)
        assertEquals(T1MapProgress.DEFAULT_SPAWN.col, migrated.playerCol)
        assertEquals(T1MapProgress.DEFAULT_SPAWN.row, migrated.playerRow)
    }

    @Test
    fun revisionOneLegacyEastCoordinateMovesToFirstTown() {
        val save = RpgSaveData(
            currentMapId = T1MapProgress.LEGACY_EAST_FIELD_MAP_ID,
            playerCol = 20,
            playerRow = 5,
            t1MapRevision = 1,
        )

        val migrated = SaveMigration.migrate(save)

        assertEquals(T1MapProgress.FIRST_TOWN_MAP_ID, migrated.currentMapId)
        assertEquals(T1MapProgress.DEFAULT_SPAWN.col, migrated.playerCol)
        assertEquals(T1MapProgress.DEFAULT_SPAWN.row, migrated.playerRow)
    }

    @Test
    fun revisionThreeLegacyDeepGateCoordinateMovesToOutpost() {
        val save = RpgSaveData(
            currentMapId = T1MapProgress.LEGACY_PLANET_MAP_ID,
            playerCol = T1MapProgress.LEGACY_DEEP_GATE_APPROACH_SPAWN.col,
            playerRow = T1MapProgress.LEGACY_DEEP_GATE_APPROACH_SPAWN.row,
            t1MapRevision = 3,
        )

        val migrated = SaveMigration.migrate(save)

        assertEquals(T1MapProgress.OUTPOST_MAP_ID, migrated.currentMapId)
        assertEquals(T1MapProgress.OUTPOST_DEEP_GATE_SPAWN.col, migrated.playerCol)
        assertEquals(T1MapProgress.OUTPOST_DEEP_GATE_SPAWN.row, migrated.playerRow)
        assertTrue(migrated.reachedT1Outpost)
        assertFalse(migrated.t1OutpostWarpUnlocked)
    }

    @Test
    fun validRanchCoordinateIsPreserved() {
        val save = RpgSaveData(currentMapId = T1MapProgress.RANCH_MAP_ID, playerCol = 10, playerRow = 9)

        val migrated = SaveMigration.migrate(save)

        assertEquals(T1MapProgress.RANCH_MAP_ID, migrated.currentMapId)
        assertEquals(10, migrated.playerCol)
        assertEquals(9, migrated.playerRow)
    }

    @Test
    fun validOutpostCoordinateIsPreserved() {
        val save = RpgSaveData(
            currentMapId = T1MapProgress.OUTPOST_MAP_ID,
            playerCol = T1MapProgress.OUTPOST_ENTRANCE_SPAWN.col,
            playerRow = T1MapProgress.OUTPOST_ENTRANCE_SPAWN.row,
            reachedT1Outpost = true,
        )

        val migrated = SaveMigration.migrate(save)

        assertEquals(T1MapProgress.OUTPOST_MAP_ID, migrated.currentMapId)
        assertEquals(T1MapProgress.OUTPOST_ENTRANCE_SPAWN.col, migrated.playerCol)
        assertEquals(T1MapProgress.OUTPOST_ENTRANCE_SPAWN.row, migrated.playerRow)
        assertTrue(migrated.reachedT1Outpost)
    }

    @Test
    fun outOfRangeCoordinateReturnsToSafeSpawn() {
        val save = RpgSaveData(currentMapId = T1MapProgress.FIRST_TOWN_MAP_ID, playerCol = 999, playerRow = -10)

        val migrated = SaveMigration.migrate(save)

        assertEquals(T1MapProgress.FIRST_TOWN_MAP_ID, migrated.currentMapId)
        assertEquals(T1MapProgress.DEFAULT_SPAWN.col, migrated.playerCol)
        assertEquals(T1MapProgress.DEFAULT_SPAWN.row, migrated.playerRow)
    }

    @Test
    fun blockedCoordinateReturnsToSafeSpawn() {
        val save = RpgSaveData(currentMapId = T1MapProgress.FIRST_TOWN_MAP_ID, playerCol = 0, playerRow = 0)

        val migrated = SaveMigration.migrate(save)

        assertEquals(T1MapProgress.FIRST_TOWN_MAP_ID, migrated.currentMapId)
        assertEquals(T1MapProgress.DEFAULT_SPAWN.col, migrated.playerCol)
        assertEquals(T1MapProgress.DEFAULT_SPAWN.row, migrated.playerRow)
    }

    @Test
    fun unknownMapIdReturnsToSafeMap() {
        val save = RpgSaveData(currentMapId = "deleted_map", playerCol = 5, playerRow = 5)

        val migrated = SaveMigration.migrate(save)

        assertEquals(T1MapProgress.DEFAULT_MAP_ID, migrated.currentMapId)
        assertEquals(T1MapProgress.DEFAULT_SPAWN.col, migrated.playerCol)
        assertEquals(T1MapProgress.DEFAULT_SPAWN.row, migrated.playerRow)
    }

    @Test
    fun migrationIsIdempotent() {
        val save = RpgSaveData(currentMapId = "deleted_map", playerCol = 999, playerRow = 999)

        val once = SaveMigration.migrate(save)
        val twice = SaveMigration.migrate(once)

        assertEquals(once, twice)
    }

    @Test
    fun t1ClearStateIsPreservedAndUsesGateApproachWhenLocationIsInvalid() {
        val save = RpgSaveData(
            currentMapId = "deleted_map",
            playerCol = 0,
            playerRow = 0,
            t1BossCleared = true,
            t1ClearAcknowledged = true,
        )

        val migrated = SaveMigration.migrate(save)

        assertTrue(migrated.t1BossCleared)
        assertTrue(migrated.t1ClearAcknowledged)
        assertEquals(T1MapProgress.DEFAULT_MAP_ID, migrated.currentMapId)
        assertEquals(T1MapProgress.DEFAULT_SPAWN.col, migrated.playerCol)
        assertEquals(T1MapProgress.DEFAULT_SPAWN.row, migrated.playerRow)
    }
}
