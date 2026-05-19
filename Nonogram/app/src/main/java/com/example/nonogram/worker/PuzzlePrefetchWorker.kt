package com.example.nonogram.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.nonogram.data.model.PuzzleCategory
import com.example.nonogram.data.repository.PuzzleRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class PuzzlePrefetchWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: PuzzleRepository,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            PuzzleCategory.entries.forEach { category ->
                repository.refreshList(category)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "puzzle_prefetch"
    }
}
