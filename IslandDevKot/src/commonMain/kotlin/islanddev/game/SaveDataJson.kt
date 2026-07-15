package islanddev.game

import islanddev.model.SaveData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object SaveDataJson {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(data: SaveData): String = json.encodeToString(data)

    fun decode(encoded: String): SaveData = json.decodeFromString(encoded)
}
