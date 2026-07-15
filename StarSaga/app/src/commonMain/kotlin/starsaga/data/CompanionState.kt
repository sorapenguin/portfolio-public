package starsaga.data

import kotlinx.serialization.Serializable

@Serializable
data class CompanionState(
    val instanceId: String,
    val creatureId: Int,
    val level: Int = 1,
    val exp: Int = 0,
    val hp: Int,
    val mp: Int,
    val skillIds: List<Int> = emptyList(),
)
