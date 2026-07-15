package starterra.area

import starterra.entity.ActorState
import starterra.world.GridCell
import starterra.world.SpikeMap

enum class AreaId { STAR_OUTPOST, SIGNAL_FIELD }

data class AreaDefinition(
    val id: AreaId,
    val map: SpikeMap,
    val defaultSpawn: GridCell,
    val gateCells: Set<GridCell>,
    val scenery: List<ActorState>,
)

object Areas {
    val starOutpost = AreaDefinition(
        AreaId.STAR_OUTPOST,
        SpikeMap.createDebugMap(),
        GridCell(2, 2),
        setOf(GridCell(7, 2), GridCell(8, 2)),
        listOf(
            ActorState("starCore", GridCell(8, 11)), ActorState("antenna", GridCell(12, 6)),
            ActorState("crateA", GridCell(4, 16)), ActorState("crateB", GridCell(12, 17)),
            ActorState("treeA", GridCell(4, 12)), ActorState("treeB", GridCell(11, 20)),
        ),
    )
    val signalField = AreaDefinition(
        AreaId.SIGNAL_FIELD,
        SpikeMap.createSignalFieldMap(),
        GridCell(7, 19),
        setOf(GridCell(7, 21), GridCell(8, 21)),
        listOf(
            ActorState("relay", GridCell(8, 11)), ActorState("beaconA", GridCell(5, 7)),
            ActorState("beaconB", GridCell(11, 12)), ActorState("beaconC", GridCell(6, 17)),
            ActorState("terminal", GridCell(12, 14)), ActorState("rockA", GridCell(3, 8)),
            ActorState("rockB", GridCell(12, 8)), ActorState("rockC", GridCell(4, 15)),
            ActorState("shrubA", GridCell(11, 18)),
        ),
    )

    fun definition(id: AreaId): AreaDefinition = if (id == AreaId.STAR_OUTPOST) starOutpost else signalField

    fun isGateAdjacent(area: AreaDefinition, cell: GridCell): Boolean =
        cell !in area.gateCells && area.gateCells.any { gate -> isOrthogonallyAdjacent(cell, gate) }

    fun isOrthogonallyAdjacent(first: GridCell, second: GridCell): Boolean =
        kotlin.math.abs(first.column - second.column) + kotlin.math.abs(first.row - second.row) == 1
}
