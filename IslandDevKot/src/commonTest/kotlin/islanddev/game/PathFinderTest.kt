package islanddev.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PathFinderTest {
    @Test
    fun findsShortestGridPath() {
        val path = PathFinder.findPath(
            start = GridPoint(0, 0),
            goal = GridPoint(2, 1),
            width = 4,
            height = 4,
            isPassable = { true }
        )

        assertEquals(3, path.size)
        assertEquals(GridPoint(2, 1), path.last())
    }

    @Test
    fun blockedGoalReturnsEmptyPath() {
        val path = PathFinder.findPath(
            start = GridPoint(0, 0),
            goal = GridPoint(1, 0),
            width = 4,
            height = 4,
            isPassable = { it != GridPoint(1, 0) }
        )

        assertTrue(path.isEmpty())
    }
}
