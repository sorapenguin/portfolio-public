package com.example.hoshipost.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.hoshipost.domain.model.StageProgress
import com.example.hoshipost.domain.model.StageResult
import kotlinx.coroutines.flow.first

private val Context.progressDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "hoshipost_progress",
)

class ProgressDataStore(context: Context) : ProgressRepository {

    private val dataStore = context.applicationContext.progressDataStore

    override suspend fun save(result: StageResult) {
        if (!result.cleared) return

        dataStore.edit { preferences ->
            val starsKey = bestStarsKey(result.stageId)
            val stepsKey = bestStepsKey(result.stageId)
            val countKey = clearedCountKey(result.stageId)
            val timeKey = updatedAtKey(result.stageId)

            val currentStars = preferences[starsKey]
            val currentSteps = preferences[stepsKey]
            val shouldUpdateBest = currentStars == null ||
                currentSteps == null ||
                result.stars > currentStars ||
                (result.stars == currentStars && result.playerSteps < currentSteps)

            if (shouldUpdateBest) {
                preferences[starsKey] = result.stars
                preferences[stepsKey] = result.playerSteps
            }

            preferences[countKey] = (preferences[countKey] ?: 0) + 1
            preferences[timeKey] = result.clearedAt
        }
    }

    override suspend fun load(stageId: String): StageProgress? {
        val preferences = dataStore.data.first()
        val bestStars = preferences[bestStarsKey(stageId)] ?: return null
        val bestSteps = preferences[bestStepsKey(stageId)] ?: return null

        return StageProgress(
            stageId = stageId,
            bestStars = bestStars,
            bestSteps = bestSteps,
            clearedCount = preferences[clearedCountKey(stageId)] ?: 0,
            updatedAt = preferences[updatedAtKey(stageId)] ?: 0L,
        )
    }

    override suspend fun loadAll(): List<StageProgress> {
        val preferences = dataStore.data.first()
        val stageIds = preferences.asMap().keys
            .mapNotNull { key -> key.name.extractStageIdFromStarsKey() }
            .distinct()

        return stageIds.mapNotNull { stageId ->
            val bestStars = preferences[bestStarsKey(stageId)] ?: return@mapNotNull null
            val bestSteps = preferences[bestStepsKey(stageId)] ?: return@mapNotNull null

            StageProgress(
                stageId = stageId,
                bestStars = bestStars,
                bestSteps = bestSteps,
                clearedCount = preferences[clearedCountKey(stageId)] ?: 0,
                updatedAt = preferences[updatedAtKey(stageId)] ?: 0L,
            )
        }.sortedBy { it.stageId.toLongOrNull() ?: Long.MAX_VALUE }
    }

    override suspend fun saveLastStageId(stageId: String) {
        dataStore.edit { preferences ->
            preferences[LAST_STAGE_ID_KEY] = stageId
        }
    }

    override suspend fun loadLastStageId(): String? =
        dataStore.data.first()[LAST_STAGE_ID_KEY]

    private fun bestStarsKey(stageId: String): Preferences.Key<Int> =
        intPreferencesKey("progress_${stageId}_stars")

    private fun bestStepsKey(stageId: String): Preferences.Key<Int> =
        intPreferencesKey("progress_${stageId}_steps")

    private fun clearedCountKey(stageId: String): Preferences.Key<Int> =
        intPreferencesKey("progress_${stageId}_count")

    private fun updatedAtKey(stageId: String): Preferences.Key<Long> =
        longPreferencesKey("progress_${stageId}_time")

    private fun String.extractStageIdFromStarsKey(): String? {
        if (!startsWith(PROGRESS_PREFIX) || !endsWith(STARS_SUFFIX)) return null
        return removePrefix(PROGRESS_PREFIX).removeSuffix(STARS_SUFFIX)
    }

    private companion object {
        const val PROGRESS_PREFIX = "progress_"
        const val STARS_SUFFIX = "_stars"
        val LAST_STAGE_ID_KEY = stringPreferencesKey("last_stage_id")
    }
}
