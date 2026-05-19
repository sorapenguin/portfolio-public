package com.example.idlegame.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.idlegame.IdleGameApp
import com.example.idlegame.network.ApiResult
import com.example.idlegame.network.TokenManager

class GameSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val token = TokenManager.getToken(applicationContext)
            ?: return Result.success() // ゲストは同期スキップ

        val app = applicationContext as IdleGameApp
        val state = app.repository.load()

        return when (val result = app.apiRepository.saveGameState(token, state)) {
            is ApiResult.Success  -> Result.success()
            is ApiResult.Offline  -> Result.retry()   // ネットワーク待ち
            is ApiResult.Failure  -> {
                // 401: トークン期限切れ→再ログインが必要、リトライしない
                if (result.code == 401) Result.success() else Result.retry()
            }
        }
    }

    companion object {
        const val WORK_NAME_PERIODIC   = "game_sync_periodic"
        const val WORK_NAME_IMMEDIATE  = "game_sync_immediate"
    }
}
