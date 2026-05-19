package com.example.nonogram.worker

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.nonogram.NonogramApplication.Companion.CHANNEL_ID
import com.example.nonogram.R
import com.example.nonogram.data.local.StaminaPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class StaminaRecoveryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val staminaPreferences: StaminaPreferences,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val stamina = staminaPreferences.get()
        if (stamina > 0) {
            showNotification(stamina)
        }
        return Result.success()
    }

    private fun showNotification(stamina: Int) {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("スタミナが回復しました！")
            .setContentText("現在のスタミナ: $stamina / ${StaminaPreferences.MAX}")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val WORK_NAME = "stamina_recovery"
        private const val NOTIFICATION_ID = 1001
    }
}
