package starsaga.save

import starsaga.data.RpgSaveData

class SaveManager : ISaveManager {
    private var data: RpgSaveData? = null

    override suspend fun save(data: RpgSaveData): RpgSaveData {
        val timestamped = data.copy(lastSavedSec = System.currentTimeMillis() / 1000)
        this.data = timestamped
        return timestamped
    }

    override suspend fun load(): RpgSaveData? = data

    override suspend fun delete() {
        data = null
    }
}
