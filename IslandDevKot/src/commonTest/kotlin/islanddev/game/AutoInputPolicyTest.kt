package islanddev.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoInputPolicyTest {
    @Test
    fun canAdvanceReturnsTrueWhenAllConditionsAreReady() {
        assertTrue(
            AutoInputPolicy.canAdvance(
                enabled = true,
                isModalOpen = false,
                playerIdle = true,
                thinkCooldownSeconds = 0.0
            )
        )
    }

    @Test
    fun canAdvanceReturnsFalseWhenAutoIsDisabled() {
        assertFalse(
            AutoInputPolicy.canAdvance(
                enabled = false,
                isModalOpen = false,
                playerIdle = true,
                thinkCooldownSeconds = 0.0
            )
        )
    }

    @Test
    fun canAdvanceReturnsFalseWhenModalIsOpen() {
        assertFalse(
            AutoInputPolicy.canAdvance(
                enabled = true,
                isModalOpen = true,
                playerIdle = true,
                thinkCooldownSeconds = 0.0
            )
        )
    }

    @Test
    fun canAdvanceReturnsFalseWhenPlayerIsMoving() {
        assertFalse(
            AutoInputPolicy.canAdvance(
                enabled = true,
                isModalOpen = false,
                playerIdle = false,
                thinkCooldownSeconds = 0.0
            )
        )
    }

    @Test
    fun canAdvanceReturnsFalseDuringCooldown() {
        assertFalse(
            AutoInputPolicy.canAdvance(
                enabled = true,
                isModalOpen = false,
                playerIdle = true,
                thinkCooldownSeconds = 0.1
            )
        )
    }

    @Test
    fun updateThinkCooldownReturnsZeroWhenAutoIsDisabled() {
        assertEquals(
            0.0,
            AutoInputPolicy.updateThinkCooldown(
                currentSeconds = 0.1,
                deltaSeconds = 0.05,
                enabled = false,
                isModalOpen = false,
                playerIdle = true
            ),
            absoluteTolerance = 0.0001
        )
    }

    @Test
    fun updateThinkCooldownResetsToThinkIntervalWhenModalIsOpen() {
        assertEquals(
            AutoInputPolicy.THINK_INTERVAL_SECONDS,
            AutoInputPolicy.updateThinkCooldown(
                currentSeconds = 0.0,
                deltaSeconds = 0.05,
                enabled = true,
                isModalOpen = true,
                playerIdle = true
            ),
            absoluteTolerance = 0.0001
        )
    }

    @Test
    fun updateThinkCooldownResetsToThinkIntervalWhenPlayerIsMoving() {
        assertEquals(
            AutoInputPolicy.THINK_INTERVAL_SECONDS,
            AutoInputPolicy.updateThinkCooldown(
                currentSeconds = 0.0,
                deltaSeconds = 0.05,
                enabled = true,
                isModalOpen = false,
                playerIdle = false
            ),
            absoluteTolerance = 0.0001
        )
    }

    @Test
    fun updateThinkCooldownSubtractsDeltaWhileReady() {
        assertEquals(
            0.05,
            AutoInputPolicy.updateThinkCooldown(
                currentSeconds = 0.10,
                deltaSeconds = 0.05,
                enabled = true,
                isModalOpen = false,
                playerIdle = true
            ),
            absoluteTolerance = 0.0001
        )
    }

    @Test
    fun updateThinkCooldownDoesNotGoBelowZero() {
        assertEquals(
            0.0,
            AutoInputPolicy.updateThinkCooldown(
                currentSeconds = 0.03,
                deltaSeconds = 0.05,
                enabled = true,
                isModalOpen = false,
                playerIdle = true
            ),
            absoluteTolerance = 0.0001
        )
    }
}
