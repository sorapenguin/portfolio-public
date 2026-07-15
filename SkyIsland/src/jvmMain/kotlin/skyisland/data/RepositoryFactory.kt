package skyisland.data

actual fun createSaveRepository(): SaveRepository = InMemorySaveRepository()
actual fun createBalanceLogRepository(): BalanceLogRepository = NoOpBalanceLogRepository()

