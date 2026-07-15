package skyisland.game

import kotlin.random.Random
import skyisland.data.Difficulty
import skyisland.data.GameTables
import skyisland.data.Ids

class DungeonGenerator(private val random: Random = Random.Default) {
    fun generate(difficulty: Difficulty, floorId: String = Ids.FLOOR_01, tutorial: Boolean = false): Dungeon {
        val chunkRows = when (floorId) {
            Ids.FLOOR_03 -> 3
            Ids.FLOOR_02 -> 3
            else -> 2
        }
        val chunkCols = when (floorId) {
            Ids.FLOOR_03 -> 3
            else -> 2
        }
        val width = CHUNK_SIZE * chunkCols
        val height = CHUNK_SIZE * chunkRows
        val tiles = mutableMapOf<Cell, Tile>()
        for (y in 0 until height) for (x in 0 until width) tiles[Cell(x, y)] = Tile.FLOOR
        for (x in 0 until width) {
            tiles[Cell(x, 0)] = Tile.WALL
            tiles[Cell(x, height - 1)] = Tile.WALL
        }
        for (y in 0 until height) {
            tiles[Cell(0, y)] = Tile.WALL
            tiles[Cell(width - 1, y)] = Tile.WALL
        }
        tiles[Cell(width - 2, height - 2)] = Tile.EXIT
        val enemies = mutableListOf<Enemy>()
        var nextId = 1
        if (tutorial) {
            enemies += enemy(nextId++, Ids.ENEMY_CLOUD_SLIME, Cell(4, 1))
        } else if (floorId == Ids.FLOOR_03) {
            val count = if (difficulty == Difficulty.RELAXED) 12 else 20
            repeat(count) {
                val id = when (it % 3) {
                    0 -> Ids.ENEMY_STONE_GOLEM
                    1 -> Ids.ENEMY_ROCK_BIRD
                    else -> Ids.ENEMY_THUNDER_BUG
                }
                enemies += enemy(nextId++, id, freeCell(tiles, enemies.map { enemy -> enemy.cell }.toSet(), width, height))
            }
            enemies += enemy(nextId, Ids.BOSS_LUMEN, Cell(width - 3, height - 3))
        } else if (floorId == Ids.FLOOR_02) {
            val count = if (difficulty == Difficulty.RELAXED) 8 else 14
            repeat(count) {
                val id = when (it % 4) {
                    0 -> Ids.ENEMY_THUNDER_BUG
                    1 -> Ids.ENEMY_MIST_JELLYFISH
                    else -> Ids.ENEMY_WIND_BIRD
                }
                enemies += enemy(nextId++, id, freeCell(tiles, enemies.map { enemy -> enemy.cell }.toSet(), width, height))
                if (id == Ids.ENEMY_MIST_JELLYFISH && it % 4 == 1) {
                    enemies += enemy(nextId++, Ids.ENEMY_MIST_JELLYFISH, freeCell(tiles, enemies.map { enemy -> enemy.cell }.toSet(), width, height))
                }
            }
            enemies += enemy(nextId, Ids.BOSS_TEMPE, Cell(width - 3, height - 3))
        } else {
            val count = if (difficulty == Difficulty.RELAXED) 6 else 12
            repeat(count) {
                val id = if (it % 3 == 0) Ids.ENEMY_WIND_BIRD else Ids.ENEMY_CLOUD_SLIME
                enemies += enemy(nextId++, id, freeCell(tiles, enemies.map { enemy -> enemy.cell }.toSet(), width, height))
            }
            enemies += enemy(nextId, Ids.BOSS_KASUMI, Cell(width - 3, height - 3))
        }
        val herbs = mutableListOf(GroundItem(Ids.ITEM_HEAL_HERB, if (tutorial) Cell(2, 2) else Cell(8, 8)))
        val chests = when (floorId) {
            Ids.FLOOR_03 -> mutableSetOf(
                Cell(4, 4), Cell(14, 4), Cell(24, 4),
                Cell(4, 14), Cell(14, 14), Cell(24, 14),
                Cell(4, 24), Cell(14, 24), Cell(24, 24),
            )
            Ids.FLOOR_02 -> mutableSetOf(Cell(6, 6), Cell(13, 6), Cell(6, 13), Cell(13, 13), Cell(6, 23), Cell(13, 23))
            else -> mutableSetOf(Cell(6, 6), Cell(13, 6), Cell(6, 13), Cell(13, 13))
        }
        return Dungeon(difficulty, tiles, enemies, herbs, chests, floorId = floorId)
    }

    private fun enemy(instanceId: Int, enemyId: String, cell: Cell) =
        Enemy(instanceId, enemyId, cell, GameTables.enemies.getValue(enemyId).maxHp)

    private fun freeCell(tiles: Map<Cell, Tile>, occupied: Set<Cell>, width: Int, height: Int): Cell {
        while (true) {
            val cell = Cell(random.nextInt(1, width - 1), random.nextInt(1, height - 1))
            if (tiles[cell] == Tile.FLOOR && cell !in occupied && cell != Cell(1, 1)) return cell
        }
    }

    companion object {
        const val CHUNK_SIZE = 10
        // Legacy floor-1 dimensions kept for older tests and call sites; generation itself is dynamic.
        const val WIDTH = CHUNK_SIZE * 2
        const val HEIGHT = CHUNK_SIZE * 2
    }
}
