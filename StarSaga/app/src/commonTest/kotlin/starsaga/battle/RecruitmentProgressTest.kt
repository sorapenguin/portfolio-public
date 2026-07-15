package starsaga.battle

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecruitmentProgressTest {
    @Test
    fun normalProgressAdvancesByOne() {
        val result = RecruitmentProgress.advance(
            currentProgress = 2,
            threshold = 5,
            hasLuckRole = false,
            random = ZeroRandom,
        )

        assertEquals(2, result.before)
        assertEquals(3, result.after)
        assertEquals(1, result.amount)
        assertFalse(result.luckBonus)
        assertFalse(result.completed)
    }

    @Test
    fun luckBonusAdvancesByTwoWhenTriggered() {
        val result = RecruitmentProgress.advance(
            currentProgress = 2,
            threshold = 5,
            hasLuckRole = true,
            random = ZeroRandom,
        )

        assertEquals(4, result.after)
        assertEquals(2, result.amount)
        assertTrue(result.luckBonus)
    }

    @Test
    fun progressDoesNotExceedThreshold() {
        val result = RecruitmentProgress.advance(
            currentProgress = 4,
            threshold = 5,
            hasLuckRole = true,
            random = ZeroRandom,
        )

        assertEquals(5, result.after)
        assertEquals(1, result.amount)
        assertTrue(result.completed)
    }

    private object ZeroRandom : Random() {
        override fun nextBits(bitCount: Int): Int = 0
    }
}
