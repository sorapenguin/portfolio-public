package islanddev.scene

import islanddev.data.GameData
import kotlin.test.Test
import kotlin.test.assertEquals

class MapLabelsTest {
    @Test
    fun beachResourcesUseReadableLabels() {
        assertEquals("木", MapLabels.resource(GameData.RES_WOOD))
        assertEquals("繊", MapLabels.resource(GameData.RES_FIBER))
        assertEquals("貝", MapLabels.resource(GameData.RES_SHELL))
    }

    @Test
    fun beachEnemiesUseReadableLabels() {
        assertEquals("ク", MapLabels.enemy(0))
        assertEquals("蟹", MapLabels.enemy(1))
    }

    @Test
    fun bossesUseFirstCharacterOfBossName() {
        assertEquals("砂", MapLabels.boss(0))
        assertEquals("森", MapLabels.boss(1))
    }

    @Test
    fun unknownIdsUseSafeFallback() {
        assertEquals("?", MapLabels.resource(999))
        assertEquals("敵", MapLabels.enemy(999))
        assertEquals("B", MapLabels.boss(999))
    }

    @Test
    fun playerUsesReadableLabel() {
        assertEquals("P", MapLabels.player())
    }
}
