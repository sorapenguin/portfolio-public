package skyisland.game

import skyisland.data.Difficulty
import skyisland.data.Ids

data class Cell(val x: Int, val y: Int) {
    fun neighbors() = listOf(Cell(x + 1, y), Cell(x - 1, y), Cell(x, y + 1), Cell(x, y - 1))
    fun distance(other: Cell) = kotlin.math.abs(x - other.x) + kotlin.math.abs(y - other.y)
}
data class Chunk(val x: Int, val y: Int)
enum class Tile { FLOOR, WALL, EXIT }
enum class RunEnd { CLEAR, ESCAPE, FORCED_EXIT, DEATH }
enum class TutorialStep { MOVE, DEFEAT_SLIME, PICK_HERB, USE_WIND_BLADE, REVEAL_FOG, COMPLETE }

data class Enemy(
    val instanceId: Int,
    val enemyId: String,
    var cell: Cell,
    var hp: Int,
    var turn: Int = 0,
    var stunTurns: Int = 0,
)
data class GroundItem(val itemId: String, val cell: Cell)
data class Player(
    var cell: Cell = Cell(1, 1),
    var hp: Int = 50,
    var shieldTurns: Int = 0,
    var stunTurns: Int = 0,
    var invincibleTurns: Int = 0,
    var lastDx: Int = 0,
    var lastDy: Int = 1,
)
data class RunStats(
    var revealedChunks: Int = 0,
    var defeatedEnemies: Int = 0,
    val materials: MutableMap<String, Int> = mutableMapOf(),
)
data class RunResult(
    val reason: RunEnd,
    val revealedChunks: Int,
    val defeatedEnemies: Int,
    val materials: Map<String, Int>,
    val message: String,
    val nextHint: String?,
)
data class Dungeon(
    val difficulty: Difficulty,
    val tiles: MutableMap<Cell, Tile>,
    val enemies: MutableList<Enemy>,
    val groundItems: MutableList<GroundItem>,
    val chests: MutableSet<Cell>,
    val visitedChunks: MutableSet<Chunk> = mutableSetOf(Chunk(0, 0)),
    var bossDefeated: Boolean = false,
    var bossFogTurns: Int = 0,
    val floorId: String = Ids.FLOOR_01,
    var lightningPreview: Set<Cell> = emptySet(),
)
