package islanddev.game

import islanddev.model.SaveData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubZoneManagerTest {
    @Test
    fun developmentStartsWhenRequirementsAreMet() {
        val save = SaveData(idea = 100)

        val result = SubZoneManager.startDevelopment(save, subZoneId = 1, nowSec = 1_000L)!!

        assertEquals(50, result.idea)
        assertEquals(2_800L, result.developingSubZones[1])
    }

    @Test
    fun elapsedDevelopmentUnlocksSubZone() {
        val save = SaveData(developingSubZones = mapOf(1 to 2_800L))

        val result = SubZoneManager.tickUnlock(save, nowSec = 2_800L)

        assertTrue(1 in result.unlockedSubZoneIds)
        assertTrue(1 !in result.developingSubZones)
    }

    @Test
    fun insufficientIdeaReturnsNull() {
        assertNull(
            SubZoneManager.startDevelopment(
                SaveData(idea = 49),
                subZoneId = 1,
                nowSec = 0L
            )
        )
    }

    @Test
    fun lockedPreviousSubZoneReturnsNull() {
        assertNull(
            SubZoneManager.startDevelopment(
                SaveData(idea = 100),
                subZoneId = 2,
                nowSec = 0L
            )
        )
    }
}
