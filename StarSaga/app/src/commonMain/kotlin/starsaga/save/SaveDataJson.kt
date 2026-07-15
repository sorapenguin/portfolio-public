package starsaga.save

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import starsaga.data.RpgSaveData
import starsaga.map.SaveMigration

object SaveDataJson {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(data: RpgSaveData): String = json.encodeToString(data)

    fun decode(encoded: String): RpgSaveData {
        val element = json.parseToJsonElement(encoded)
        val decoded = json.decodeFromString<RpgSaveData>(encoded)
        val revisionAware = if ("t1MapRevision" in element.jsonObject) {
            decoded
        } else {
            decoded.copy(t1MapRevision = 0)
        }
        return SaveMigration.migrate(revisionAware)
    }
}
