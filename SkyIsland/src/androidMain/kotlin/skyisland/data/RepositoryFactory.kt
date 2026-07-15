package skyisland.data

import android.app.Application

private fun currentApplication(): Application =
    Class.forName("android.app.ActivityThread")
        .getMethod("currentApplication")
        .invoke(null) as Application

actual fun createSaveRepository(): SaveRepository = LocalSaveRepository(currentApplication())
actual fun createBalanceLogRepository(): BalanceLogRepository = FileBalanceLogRepository(currentApplication())

