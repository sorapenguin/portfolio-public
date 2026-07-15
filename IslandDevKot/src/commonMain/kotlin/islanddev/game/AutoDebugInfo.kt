package islanddev.game

import islanddev.data.GameData

data class AutoDebugInfo(
    val enabled: Boolean,
    val fixedZoneId: Int?,
    val candidateCount: Int,
    val target: AutoTarget?,
    val stopReason: AutoStopReason
) {
    fun displayText(): String {
        val zoneName = fixedZoneId?.let(::zoneName) ?: "-"
        val targetText = target?.let {
            val resourceName = GameData.resourceById(it.id)?.name ?: it.id.toString()
            "$resourceName(${it.objectCell.col},${it.objectCell.row})"
        } ?: "-"
        val state = if (enabled) "ON" else "OFF"
        return "AUTO $state zone:$zoneName cand:$candidateCount\n" +
            "target:$targetText stop:${stopReason.label}"
    }

    private fun zoneName(zoneId: Int): String = when (zoneId) {
        GameData.ZONE_BEACH -> "砂浜"
        GameData.ZONE_FOREST -> "森"
        GameData.ZONE_REEF -> "岩礁"
        GameData.ZONE_DEPTHS -> "奥地"
        GameData.ZONE_SUMMIT -> "山頂"
        else -> zoneId.toString()
    }
}
