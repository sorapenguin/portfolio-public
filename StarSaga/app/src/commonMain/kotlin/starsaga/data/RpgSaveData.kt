package starsaga.data

import kotlinx.serialization.Serializable
import starsaga.map.T1MapProgress

@Serializable
data class RpgSaveData(
    val version: Int = 1,
    val currentMapId: String = T1MapProgress.DEFAULT_MAP_ID,
    val playerCol: Int = T1MapProgress.DEFAULT_SPAWN.col,
    val playerRow: Int = T1MapProgress.DEFAULT_SPAWN.row,
    val t1MapRevision: Int = T1MapProgress.CURRENT_REVISION,
    val currentT1AreaId: String = T1MapProgress.DEFAULT_AREA_ID,
    val reachedT1Outpost: Boolean = false,
    val t1OutpostWarpUnlocked: Boolean = false,
    val gold: Int = 0,
    val itemCounts: Map<Int, Int> = emptyMap(),
    val companions: List<CompanionState> = emptyList(),
    val partyCompanionIds: List<String> = emptyList(),
    val activeCompanionIds: List<String> = emptyList(),
    val seenCreatureIds: Set<Int> = emptySet(),
    val befriendedCreatureIds: Set<Int> = emptySet(),
    val defeatCountByCreatureId: Map<Int, Int> = emptyMap(),
    val recruitProgressByCreatureId: Map<Int, Int> = emptyMap(),
    val trainingCompanionIds: List<String> = emptyList(),
    val lastTrainingAtEpochMillis: Long = 0L,
    val t1BossCleared: Boolean = false,
    val t1ClearAcknowledged: Boolean = false,
    val tutorialSeen: Boolean = false,
    val debugUnlimitedHeal: Boolean = false,
    val lastSavedSec: Long = 0L,
)
