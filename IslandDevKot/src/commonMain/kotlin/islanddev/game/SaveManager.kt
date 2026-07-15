package islanddev.game

import islanddev.model.SaveData

interface ISaveManager {
    suspend fun save(data: SaveData)
    suspend fun load(): SaveData
}
