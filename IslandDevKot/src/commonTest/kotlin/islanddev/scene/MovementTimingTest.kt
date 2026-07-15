package islanddev.scene

import islanddev.game.GridPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MovementTimingTest {
    @Test
    fun effectiveDeltaIsClamped() {
        assertEquals(
            PlayerSprite.MAX_DELTA_SECONDS,
            MovementTiming.effectiveDelta(0.5, PlayerSprite.MAX_DELTA_SECONDS),
            absoluteTolerance = 0.000001
        )
    }

    @Test
    fun confirmedRouteEndsAtTarget() {
        val target = GridPoint(2, 1)
        val route = ConfirmedRoute(
            start = GridPoint(0, 0),
            target = target,
            path = listOf(GridPoint(1, 0), GridPoint(2, 0), target)
        )

        assertEquals(target, route.path.last())
        assertTrue(route.isMovementRequired)
    }

    @Test
    fun emptyRouteDoesNotMove() {
        val cell = GridPoint(1, 1)
        val route = ConfirmedRoute(cell, cell, emptyList())

        assertFalse(route.isMovementRequired)
    }

    @Test
    fun routeRejectsPathEndingOutsideTarget() {
        assertFailsWith<IllegalArgumentException> {
            ConfirmedRoute(
                start = GridPoint(0, 0),
                target = GridPoint(2, 0),
                path = listOf(GridPoint(1, 0))
            )
        }
    }
}
