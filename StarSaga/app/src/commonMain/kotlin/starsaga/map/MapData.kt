package starsaga.map

enum class TileType(val passable: Boolean) {
    Floor(passable = true),
    TownFloor(passable = true),
    Road(passable = true),
    Wall(passable = false),
    Grass(passable = true),
    Exit(passable = true),
    Heal(passable = true),
    Shop(passable = true),
    Ranch(passable = true),
    TrainingPad(passable = true),
    StarLamp(passable = false),
    Sign(passable = false),
    Planned(passable = false),
    DeepGate(passable = false),
    MeteorRock(passable = false),
    Crystal(passable = false),
    EnergyFence(passable = false),
}

data class GridCell(val col: Int, val row: Int)

enum class ExitSide {
    East,
    West,
}

data class MapExit(
    val id: String,
    val side: ExitSide,
    // Inner handoff column for edge transitions. This is intentionally not the
    // actual map edge, so taps 1-2 tiles before the edge can trigger travel.
    val triggerCol: Int,
    val triggerRows: IntRange,
    val targetMapId: String,
    val targetSpawn: GridCell,
    val lockedSideOnArrival: ExitSide,
)

class MapData(
    val id: String,
    val columns: Int,
    val rows: Int,
    private val tiles: List<TileType>,
    val exits: List<MapExit> = emptyList(),
) {
    fun tileAt(col: Int, row: Int): TileType? {
        if (col !in 0 until columns || row !in 0 until rows) return null
        return tiles[row * columns + col]
    }

    fun isPassable(col: Int, row: Int): Boolean = tileAt(col, row)?.passable == true

    fun exitForMove(before: GridCell, after: GridCell): MapExit? =
        exits.firstOrNull { exit ->
            after.row in exit.triggerRows &&
                when (exit.side) {
                    ExitSide.East -> before.col < exit.triggerCol && after.col >= exit.triggerCol
                    ExitSide.West -> before.col > exit.triggerCol && after.col <= exit.triggerCol
                }
        }

    companion object {
        const val TILE_SIZE = 32

        fun get(mapId: String): MapData = when (mapId) {
            T1MapProgress.FIRST_TOWN_MAP_ID -> createT1FirstTown()
            T1MapProgress.SETTLEMENT_OUTSKIRTS_MAP_ID -> createT1SettlementOutskirts()
            T1MapProgress.STARGRASS_FORK_MAP_ID -> createT1StargrassFork()
            T1MapProgress.DEEP_GATE_ROAD_MAP_ID -> createT1DeepGateRoad()
            T1MapProgress.OUTPOST_MAP_ID -> createT1Outpost()
            "planet_t1" -> createDebugPlanet()
            "planet_t1_east" -> createDebugPlanetEast()
            "ranch" -> createRanch()
            else -> createT1FirstTown()
        }

        private fun createT1FirstTown(): MapData {
            val columns = 24
            val rows = 20
            val tiles = MutableList(columns * rows) { TileType.TownFloor }

            fun set(col: Int, row: Int, tile: TileType) {
                if (col in 0 until columns && row in 0 until rows) {
                    tiles[row * columns + col] = tile
                }
            }

            fun setRect(colRange: IntRange, rowRange: IntRange, tile: TileType) {
                for (row in rowRange) {
                    for (col in colRange) set(col, row, tile)
                }
            }

            for (col in 0 until columns) {
                set(col, 0, TileType.Wall)
                set(col, rows - 1, TileType.Wall)
            }
            for (row in 0 until rows) {
                set(0, row, TileType.Wall)
                set(columns - 1, row, TileType.Wall)
            }

            setRect(4..20, 9..11, TileType.Road)
            setRect(11..13, 4..16, TileType.Road)
            setRect(4..16, 4..6, TileType.TownFloor)
            setRect(4..10, 13..15, TileType.TownFloor)

            set(5, 5, TileType.Heal)
            set(6, 5, TileType.Heal)
            set(9, 5, TileType.Shop)
            set(10, 5, TileType.Shop)
            set(14, 5, TileType.Ranch)
            set(15, 5, TileType.Ranch)
            set(5, 14, TileType.TrainingPad)
            set(7, 14, TileType.TrainingPad)
            set(9, 14, TileType.TrainingPad)
            set(18, 8, TileType.Sign)
            set(18, 12, TileType.Sign)
            set(3, 8, TileType.StarLamp)
            set(3, 12, TileType.StarLamp)
            set(20, 8, TileType.StarLamp)
            set(20, 12, TileType.StarLamp)
            set(T1MapProgress.FIRST_TOWN_WARP.col, T1MapProgress.FIRST_TOWN_WARP.row, TileType.Exit)

            set(2, 15, TileType.Planned)
            set(3, 15, TileType.Sign)
            set(22, 9, TileType.Exit)
            set(22, 10, TileType.Exit)
            set(22, 11, TileType.Exit)

            return MapData(
                id = T1MapProgress.FIRST_TOWN_MAP_ID,
                columns = columns,
                rows = rows,
                tiles = tiles,
                exits = listOf(
                    MapExit(
                        id = "first_town_to_outskirts",
                        side = ExitSide.East,
                        triggerCol = 22,
                        triggerRows = 8..12,
                        targetMapId = T1MapProgress.SETTLEMENT_OUTSKIRTS_MAP_ID,
                        targetSpawn = T1MapProgress.SETTLEMENT_OUTSKIRTS_SPAWN,
                        lockedSideOnArrival = ExitSide.West,
                    ),
                ),
            )
        }

        private fun createT1SettlementOutskirts(): MapData {
            val columns = 28
            val rows = 22
            val tiles = MutableList(columns * rows) { TileType.Floor }

            fun set(col: Int, row: Int, tile: TileType) {
                if (col in 0 until columns && row in 0 until rows) {
                    tiles[row * columns + col] = tile
                }
            }

            fun setRect(colRange: IntRange, rowRange: IntRange, tile: TileType) {
                for (row in rowRange) {
                    for (col in colRange) set(col, row, tile)
                }
            }

            for (col in 0 until columns) {
                set(col, 0, TileType.Wall)
                set(col, rows - 1, TileType.Wall)
            }
            for (row in 0 until rows) {
                set(0, row, TileType.Wall)
                set(columns - 1, row, TileType.Wall)
            }

            setRect(2..24, 9..11, TileType.Road)
            setRect(6..8, 5..14, TileType.Road)
            setRect(14..16, 7..17, TileType.Road)
            setRect(19..22, 14..16, TileType.Road)

            setRect(4..10, 3..4, TileType.Grass)
            setRect(9..13, 6..8, TileType.Grass)
            setRect(17..23, 5..8, TileType.Grass)
            setRect(4..12, 13..16, TileType.Grass)
            setRect(17..23, 17..19, TileType.Grass)

            for (col in 10..13) set(col, 12, TileType.MeteorRock)
            set(18, 13, TileType.Crystal)
            set(23, 12, TileType.Sign)
            set(25, 9, TileType.Exit)
            set(25, 10, TileType.Exit)
            set(25, 11, TileType.Exit)
            set(1, 10, TileType.Exit)
            set(2, 10, TileType.Exit)

            return MapData(
                id = T1MapProgress.SETTLEMENT_OUTSKIRTS_MAP_ID,
                columns = columns,
                rows = rows,
                tiles = tiles,
                exits = listOf(
                    MapExit(
                        id = "outskirts_to_first_town",
                        side = ExitSide.West,
                        triggerCol = 2,
                        triggerRows = 8..12,
                        targetMapId = T1MapProgress.FIRST_TOWN_MAP_ID,
                        targetSpawn = T1MapProgress.FIRST_TOWN_ROAD_SPAWN,
                        lockedSideOnArrival = ExitSide.East,
                    ),
                    MapExit(
                        id = "outskirts_to_stargrass_fork",
                        side = ExitSide.East,
                        triggerCol = 25,
                        triggerRows = 8..12,
                        targetMapId = T1MapProgress.STARGRASS_FORK_MAP_ID,
                        targetSpawn = T1MapProgress.STARGRASS_FORK_WEST_SPAWN,
                        lockedSideOnArrival = ExitSide.West,
                    ),
                ),
            )
        }

        private fun createT1StargrassFork(): MapData {
            val columns = 34
            val rows = 24
            val tiles = MutableList(columns * rows) { TileType.Floor }

            fun set(col: Int, row: Int, tile: TileType) {
                if (col in 0 until columns && row in 0 until rows) {
                    tiles[row * columns + col] = tile
                }
            }

            fun setRect(colRange: IntRange, rowRange: IntRange, tile: TileType) {
                for (row in rowRange) {
                    for (col in colRange) set(col, row, tile)
                }
            }

            for (col in 0 until columns) {
                set(col, 0, TileType.Wall)
                set(col, rows - 1, TileType.Wall)
            }
            for (row in 0 until rows) {
                set(0, row, TileType.Wall)
                set(columns - 1, row, TileType.Wall)
            }

            setRect(2..31, 11..13, TileType.Road)
            setRect(8..10, 5..18, TileType.Road)
            setRect(15..17, 4..18, TileType.Road)
            setRect(22..25, 7..18, TileType.Road)
            setRect(12..20, 5..7, TileType.Road)
            setRect(12..26, 17..19, TileType.Road)

            setRect(4..12, 3..4, TileType.Grass)
            setRect(4..7, 14..19, TileType.Grass)
            setRect(11..14, 8..10, TileType.Grass)
            setRect(18..22, 4..6, TileType.Grass)
            setRect(20..29, 14..18, TileType.Grass)
            setRect(27..30, 8..10, TileType.Grass)

            set(16, 6, TileType.Heal)
            set(17, 6, TileType.Heal)
            set(13, 15, TileType.Sign)
            set(21, 8, TileType.Sign)
            set(11, 16, TileType.Crystal)
            set(18, 15, TileType.MeteorRock)
            set(1, 12, TileType.Exit)
            set(2, 12, TileType.Exit)
            set(31, 12, TileType.Exit)
            set(32, 12, TileType.Exit)

            return MapData(
                id = T1MapProgress.STARGRASS_FORK_MAP_ID,
                columns = columns,
                rows = rows,
                tiles = tiles,
                exits = listOf(
                    MapExit(
                        id = "stargrass_fork_to_outskirts",
                        side = ExitSide.West,
                        triggerCol = 2,
                        triggerRows = 10..14,
                        targetMapId = T1MapProgress.SETTLEMENT_OUTSKIRTS_MAP_ID,
                        targetSpawn = T1MapProgress.SETTLEMENT_OUTSKIRTS_FUTURE_EXIT_APPROACH,
                        lockedSideOnArrival = ExitSide.East,
                    ),
                    MapExit(
                        id = "stargrass_fork_to_deep_gate_road",
                        side = ExitSide.East,
                        triggerCol = 31,
                        triggerRows = 10..14,
                        targetMapId = T1MapProgress.DEEP_GATE_ROAD_MAP_ID,
                        targetSpawn = T1MapProgress.DEEP_GATE_ROAD_WEST_SPAWN,
                        lockedSideOnArrival = ExitSide.West,
                    ),
                ),
            )
        }

        private fun createT1DeepGateRoad(): MapData {
            val columns = 30
            val rows = 24
            val tiles = MutableList(columns * rows) { TileType.Floor }

            fun set(col: Int, row: Int, tile: TileType) {
                if (col in 0 until columns && row in 0 until rows) {
                    tiles[row * columns + col] = tile
                }
            }

            fun setRect(colRange: IntRange, rowRange: IntRange, tile: TileType) {
                for (row in rowRange) {
                    for (col in colRange) set(col, row, tile)
                }
            }

            for (col in 0 until columns) {
                set(col, 0, TileType.Wall)
                set(col, rows - 1, TileType.Wall)
            }
            for (row in 0 until rows) {
                set(0, row, TileType.Wall)
                set(columns - 1, row, TileType.Wall)
            }

            setRect(2..27, 11..13, TileType.Road)
            setRect(6..8, 6..17, TileType.Road)
            setRect(17..19, 5..18, TileType.Road)
            setRect(20..25, 17..19, TileType.Road)

            setRect(4..10, 4..5, TileType.Grass)
            setRect(4..12, 15..19, TileType.Grass)
            setRect(12..17, 7..10, TileType.Grass)
            setRect(20..26, 4..8, TileType.Grass)
            setRect(21..26, 14..16, TileType.Grass)

            set(22, 10, TileType.Crystal)
            set(24, 10, TileType.Crystal)
            set(25, 12, TileType.StarLamp)
            set(26, 12, TileType.Sign)
            set(13, 14, TileType.MeteorRock)
            set(1, 12, TileType.Exit)
            set(2, 12, TileType.Exit)
            set(28, 12, TileType.Exit)

            return MapData(
                id = T1MapProgress.DEEP_GATE_ROAD_MAP_ID,
                columns = columns,
                rows = rows,
                tiles = tiles,
                exits = listOf(
                    MapExit(
                        id = "deep_gate_road_to_stargrass_fork",
                        side = ExitSide.West,
                        triggerCol = 2,
                        triggerRows = 10..14,
                        targetMapId = T1MapProgress.STARGRASS_FORK_MAP_ID,
                        targetSpawn = T1MapProgress.STARGRASS_FORK_EAST_SPAWN,
                        lockedSideOnArrival = ExitSide.East,
                    ),
                    MapExit(
                        id = "deep_gate_road_to_outpost",
                        side = ExitSide.East,
                        triggerCol = 28,
                        triggerRows = 10..14,
                        targetMapId = T1MapProgress.OUTPOST_MAP_ID,
                        targetSpawn = T1MapProgress.OUTPOST_ENTRANCE_SPAWN,
                        lockedSideOnArrival = ExitSide.West,
                    ),
                ),
            )
        }

        private fun createT1Outpost(): MapData {
            val columns = 22
            val rows = 18
            val tiles = MutableList(columns * rows) { TileType.TownFloor }

            fun set(col: Int, row: Int, tile: TileType) {
                if (col in 0 until columns && row in 0 until rows) {
                    tiles[row * columns + col] = tile
                }
            }

            fun setRect(colRange: IntRange, rowRange: IntRange, tile: TileType) {
                for (row in rowRange) {
                    for (col in colRange) set(col, row, tile)
                }
            }

            for (col in 0 until columns) {
                set(col, 0, TileType.Wall)
                set(col, rows - 1, TileType.Wall)
            }
            for (row in 0 until rows) {
                set(0, row, TileType.Wall)
                set(columns - 1, row, TileType.Wall)
            }

            setRect(2..17, 8..10, TileType.Road)
            setRect(5..7, 4..14, TileType.Road)
            setRect(13..15, 4..13, TileType.Road)
            setRect(8..13, 4..6, TileType.Road)
            setRect(8..13, 12..14, TileType.Road)

            set(4, 5, TileType.Heal)
            set(5, 5, TileType.Heal)
            set(10, 5, TileType.Ranch)
            set(11, 5, TileType.Ranch)
            set(10, 13, TileType.Sign)
            set(5, 13, TileType.Exit)
            set(6, 13, TileType.Exit)
            set(15, 9, TileType.DeepGate)
            set(16, 9, TileType.DeepGate)
            set(14, 8, TileType.StarLamp)
            set(17, 8, TileType.StarLamp)
            set(14, 10, TileType.Crystal)
            set(17, 10, TileType.Crystal)
            set(1, 9, TileType.Exit)
            set(2, 9, TileType.Exit)

            return MapData(
                id = T1MapProgress.OUTPOST_MAP_ID,
                columns = columns,
                rows = rows,
                tiles = tiles,
                exits = listOf(
                    MapExit(
                        id = "outpost_to_deep_gate_road",
                        side = ExitSide.West,
                        triggerCol = 2,
                        triggerRows = 7..11,
                        targetMapId = T1MapProgress.DEEP_GATE_ROAD_MAP_ID,
                        targetSpawn = T1MapProgress.DEEP_GATE_ROAD_GATE_SPAWN,
                        lockedSideOnArrival = ExitSide.East,
                    ),
                ),
            )
        }

        private fun createDebugPlanet(): MapData {
            val columns = 60
            val rows = 40
            val tiles = MutableList(columns * rows) { TileType.Floor }

            fun set(col: Int, row: Int, tile: TileType) {
                if (col in 0 until columns && row in 0 until rows) {
                    tiles[row * columns + col] = tile
                }
            }

            fun setRect(colRange: IntRange, rowRange: IntRange, tile: TileType) {
                for (row in rowRange) {
                    for (col in colRange) set(col, row, tile)
                }
            }

            fun setFenceLine(colRange: IntRange, row: Int, gaps: Set<Int> = emptySet()) {
                for (col in colRange) {
                    if (col !in gaps) set(col, row, TileType.EnergyFence)
                }
            }

            fun setFenceColumn(col: Int, rowRange: IntRange, gaps: Set<Int> = emptySet()) {
                for (row in rowRange) {
                    if (row !in gaps) set(col, row, TileType.EnergyFence)
                }
            }

            for (col in 0 until columns) {
                set(col, 0, TileType.Wall)
                set(col, rows - 1, TileType.Wall)
            }
            for (row in 0 until rows) {
                set(0, row, TileType.Wall)
                set(columns - 1, row, TileType.Wall)
            }

            // Starport Town: protected by star lamps, so no grass encounter tiles are placed here.
            for (row in 2..12) {
                for (col in 2..18) set(col, row, TileType.TownFloor)
            }
            setFenceLine(2..17, 1)
            setFenceLine(2..17, 13)
            setFenceColumn(19, 3..12, gaps = setOf(6, 7, 8, 9))
            for (col in 18..38) set(col, 7, TileType.Road)
            for (col in 18..38) set(col, 8, TileType.Road)
            for (row in 8..22) {
                set(38, row, TileType.Road)
                set(39, row, TileType.Road)
            }
            for (col in 38..50) set(col, 22, TileType.Road)
            set(18, 6, TileType.StarLamp)
            set(18, 9, TileType.StarLamp)
            set(21, 6, TileType.Sign)
            set(30, 6, TileType.Sign)

            setFenceLine(19..37, 6, gaps = setOf(21, 30))
            setFenceLine(19..37, 9, gaps = setOf(25, 26, 32, 33))
            setRect(25..27, 10..13, TileType.Grass)
            setRect(32..34, 10..14, TileType.Grass)
            setRect(29..31, 15..17, TileType.Grass)

            setFenceColumn(37, 9..21, gaps = setOf(11, 12, 16, 17))
            setFenceColumn(40, 9..21, gaps = setOf(13, 14, 18, 19))
            setRect(41..44, 12..15, TileType.Grass)
            setRect(42..46, 18..21, TileType.Grass)
            setRect(47..50, 17..20, TileType.Grass)
            setRect(30..34, 24..27, TileType.Grass)
            setRect(35..38, 28..30, TileType.Grass)

            for (col in 22..36) {
                if (col !in 28..30) set(col, 19, TileType.MeteorRock)
            }
            for (row in 12..27) {
                if (row !in 20..23) set(55, row, TileType.MeteorRock)
            }
            set(23, 10, TileType.Crystal)
            set(28, 14, TileType.MeteorRock)
            set(35, 15, TileType.Crystal)
            set(41, 16, TileType.MeteorRock)
            set(46, 16, TileType.Crystal)
            set(52, 18, TileType.MeteorRock)
            set(29, 28, TileType.Crystal)
            set(39, 29, TileType.MeteorRock)
            set(50, 22, TileType.DeepGate)
            set(51, 22, TileType.DeepGate)
            set(49, 21, TileType.StarLamp)
            set(52, 21, TileType.StarLamp)
            set(49, 23, TileType.Crystal)
            set(52, 23, TileType.Crystal)
            set(48, 22, TileType.MeteorRock)
            set(53, 22, TileType.MeteorRock)

            set(5, 5, TileType.Heal)
            set(6, 5, TileType.Heal)
            set(9, 5, TileType.Shop)
            set(10, 5, TileType.Shop)
            set(13, 5, TileType.Ranch)
            set(14, 5, TileType.Ranch)
            set(5, 9, TileType.Planned)
            set(8, 9, TileType.Planned)
            set(11, 9, TileType.Planned)
            set(14, 9, TileType.Sign)
            set(57, 20, TileType.Exit)
            set(58, 20, TileType.Exit)

            return MapData(
                id = "planet_t1",
                columns = columns,
                rows = rows,
                tiles = tiles,
                exits = listOf(
                    MapExit(
                        id = "legacy_deep_gate_to_deep_gate_road",
                        side = ExitSide.West,
                        triggerCol = 45,
                        triggerRows = 20..24,
                        targetMapId = T1MapProgress.DEEP_GATE_ROAD_MAP_ID,
                        targetSpawn = T1MapProgress.DEEP_GATE_ROAD_GATE_SPAWN,
                        lockedSideOnArrival = ExitSide.East,
                    ),
                    MapExit(
                        id = "east_gate",
                        side = ExitSide.East,
                        triggerCol = 57,
                        triggerRows = 17..23,
                        targetMapId = "planet_t1_east",
                        targetSpawn = GridCell(3, 20),
                        lockedSideOnArrival = ExitSide.West,
                    )
                ),
            )
        }

        private fun createDebugPlanetEast(): MapData {
            val columns = 60
            val rows = 40
            val tiles = MutableList(columns * rows) { TileType.Floor }

            fun set(col: Int, row: Int, tile: TileType) {
                if (col in 0 until columns && row in 0 until rows) {
                    tiles[row * columns + col] = tile
                }
            }

            for (col in 0 until columns) {
                set(col, 0, TileType.Wall)
                set(col, rows - 1, TileType.Wall)
            }
            for (row in 0 until rows) {
                set(0, row, TileType.Wall)
                set(columns - 1, row, TileType.Wall)
            }

            for (row in 5..18) {
                for (col in 20..36) set(col, row, TileType.Grass)
            }
            for (row in 23..34) {
                for (col in 7..24) set(col, row, TileType.Grass)
            }
            for (col in 12..45) {
                if (col !in 28..30) set(col, 16, TileType.Wall)
            }
            for (row in 8..30) {
                if (row !in 21..23) set(42, row, TileType.Wall)
            }
            set(1, 20, TileType.Exit)
            set(2, 20, TileType.Exit)

            return MapData(
                id = "planet_t1_east",
                columns = columns,
                rows = rows,
                tiles = tiles,
                exits = listOf(
                    MapExit(
                        id = "west_gate",
                        side = ExitSide.West,
                        triggerCol = 2,
                        triggerRows = 17..23,
                        targetMapId = "planet_t1",
                        targetSpawn = GridCell(56, 20),
                        lockedSideOnArrival = ExitSide.East,
                    )
                ),
            )
        }

        private fun createRanch(): MapData {
            val columns = 20
            val rows = 15
            val tiles = MutableList(columns * rows) { TileType.Floor }

            fun set(col: Int, row: Int, tile: TileType) {
                if (col in 0 until columns && row in 0 until rows) {
                    tiles[row * columns + col] = tile
                }
            }

            for (col in 0 until columns) {
                set(col, 0, TileType.Wall)
                set(col, rows - 1, TileType.Wall)
            }
            for (row in 0 until rows) {
                set(0, row, TileType.Wall)
                set(columns - 1, row, TileType.Wall)
            }
            for (col in 3..16) {
                set(col, 3, TileType.Grass)
                set(col, 11, TileType.Grass)
            }
            for (row in 4..10) {
                set(3, row, TileType.Grass)
                set(16, row, TileType.Grass)
            }
            set(9, 6, TileType.Ranch)
            set(10, 6, TileType.Ranch)
            set(5, 5, TileType.TrainingPad)
            set(5, 7, TileType.TrainingPad)
            set(5, 9, TileType.TrainingPad)
            set(9, 12, TileType.Exit)
            set(10, 12, TileType.Exit)

            return MapData(
                id = "ranch",
                columns = columns,
                rows = rows,
                tiles = tiles,
            )
        }
    }
}
