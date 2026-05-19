package com.example.idlegame.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object SyncScheduler {

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /**
     * 定期バックグラウンド同期を登録する（15分毎・重複しない）。
     * アプリ起動時に一度だけ呼べばよい。KEEP ポリシーで既存ジョブを上書きしない。
     */
    fun schedulePeriodicSync(context: Context) {
        val request = PeriodicWorkRequestBuilder<GameSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            GameSyncWorker.WORK_NAME_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /**
     * 即時同期を1回実行する。
     * フォアグラウンド復帰時・ログイン直後に呼ぶ。
     * REPLACE ポリシーで前回の未実行分はキャンセルして新しいものに差し替える。
     */
    fun syncNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<GameSyncWorker>()
            .setConstraints(networkConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            GameSyncWorker.WORK_NAME_IMMEDIATE,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
