package islanddev.ui

import islanddev.data.GameData
import islanddev.model.SaveData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CraftPanelFormattingTest {
    @Test
    fun singleResourceUsesDisplayName() {
        assertEquals(
            "木材×3",
            formatResourceCost(mapOf(GameData.RES_WOOD to 3))
        )
    }

    @Test
    fun multipleResourcesUseDisplayNames() {
        assertEquals(
            "貝殻×3 繊維×2",
            formatResourceCost(
                linkedMapOf(
                    GameData.RES_SHELL to 3,
                    GameData.RES_FIBER to 2
                )
            )
        )
    }

    @Test
    fun unknownResourceUsesSafeFallback() {
        assertEquals(
            "不明素材(999)×1",
            formatResourceCost(mapOf(999 to 1))
        )
    }

    @Test
    fun recipeStatusShowsAvailabilityAndMissingAmount() {
        val cost = mapOf(GameData.RES_WOOD to 3)

        assertEquals(
            "必要: 木材×3  / 作成可能",
            recipeStatus(cost, SaveData(inventory = mapOf(GameData.RES_WOOD to 3)))
        )
        assertEquals(
            "必要: 木材×3  / 木材不足(1/3)",
            recipeStatus(cost, SaveData(inventory = mapOf(GameData.RES_WOOD to 1)))
        )
    }

    @Test
    fun remainingTimeUsesReadableClockFormat() {
        assertEquals("29:42", formatDuration(29 * 60L + 42))
        assertEquals("1:02:03", formatDuration(3723))
        assertEquals("0:00", formatDuration(-1))
    }

    @Test
    fun approximateTimeUsesCoarseIdleGameFormat() {
        assertEquals("あと1分未満", formatApproxDuration(20))
        assertEquals("あと約30分", formatApproxDuration(29 * 60L + 1))
        assertEquals("あと約2時間", formatApproxDuration(61 * 60L))
    }

    @Test
    fun autoDebugIsHiddenByDefault() {
        assertFalse(GameHUD.AUTO_DEBUG_VISIBLE)
    }
}
