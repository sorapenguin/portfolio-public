package com.example.hoshipost.data

import com.example.hoshipost.domain.model.StageProgress
import com.example.hoshipost.domain.model.StageResult

interface ProgressRepository {
    suspend fun save(result: StageResult)
    suspend fun load(stageId: String): StageProgress?
    suspend fun loadAll(): List<StageProgress>
    suspend fun saveLastStageId(stageId: String)
    suspend fun loadLastStageId(): String?
}
