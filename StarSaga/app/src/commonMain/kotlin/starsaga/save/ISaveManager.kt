package starsaga.save

import starsaga.data.RpgSaveData

interface ISaveManager {
    suspend fun save(data: RpgSaveData): RpgSaveData
    suspend fun load(): RpgSaveData?
    suspend fun delete()
}
