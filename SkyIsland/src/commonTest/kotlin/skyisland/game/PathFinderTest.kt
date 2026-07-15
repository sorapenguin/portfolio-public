package skyisland.game

import kotlin.test.Test
import kotlin.test.assertEquals

class PathFinderTest {
    @Test
    fun findsShortestPathAroundBlockedCell() {
        val path = PathFinder.find(Cell(1, 1), Cell(3, 1)) { it != Cell(2, 1) && it.x in 0..4 && it.y in 0..4 }
        assertEquals(4, path.size)
        assertEquals(Cell(3, 1), path.last())
    }
}

