package skyisland.data

interface SaveRepository {
    fun save(data: SaveData)
    fun load(): SaveData
}

interface CloudSyncRepository {
    fun isAvailable(): Boolean
    fun syncToCloud(data: SaveData): Result<Unit>
    fun syncFromCloud(): SaveData?
}

interface PurchaseRepository {
    fun isPurchased(itemId: String): Boolean
    fun purchase(itemId: String)
}

class InMemorySaveRepository(initial: SaveData = SaveData()) : SaveRepository {
    private var data = initial
    override fun save(data: SaveData) { this.data = data }
    override fun load(): SaveData = data
}

class StubCloudSyncRepository : CloudSyncRepository {
    override fun isAvailable() = false
    override fun syncToCloud(data: SaveData) = Result.failure<Unit>(UnsupportedOperationException("Cloud sync is not available in v0.1"))
    override fun syncFromCloud(): SaveData? = null
}

class StubPurchaseRepository : PurchaseRepository {
    override fun isPurchased(itemId: String) = false
    override fun purchase(itemId: String) = Unit
}

interface BalanceLogRepository {
    fun appendLines(lines: List<String>)
}

class NoOpBalanceLogRepository : BalanceLogRepository {
    override fun appendLines(lines: List<String>) = Unit
}

