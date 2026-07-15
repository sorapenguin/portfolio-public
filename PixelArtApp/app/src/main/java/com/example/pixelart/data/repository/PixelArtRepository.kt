package com.example.pixelart.data.repository

import com.example.pixelart.data.local.PuzzleDao
import com.example.pixelart.data.local.PuzzleEntity
import com.example.pixelart.data.local.UserIdManager
import com.example.pixelart.data.remote.PixelArtApiService
import com.example.pixelart.data.remote.dto.ProgressRequest
import com.example.pixelart.data.remote.dto.PuzzleListItemDto
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import retrofit2.HttpException

class PixelArtRepository(
    private val dao: PuzzleDao,
    private val api: PixelArtApiService,
    private val userIdManager: UserIdManager,
) {
    private val gson = Gson()

    companion object {
        private const val VISIBLE_SIZE = 5
        private const val FETCH_SIZE = 10
    }

    fun observeActive(width: Int): Flow<List<PuzzleEntity>> = dao.observeActive(width)

    /** 指定サイズのパズルをAPI取得してDBに蓄え、表示スロットを最大5件に補充する */
    suspend fun refreshBySize(width: Int) {
        val page = dao.countAll(width) / FETCH_SIZE
        val dtos = runCatching { api.getPuzzles(size = width, page = page, limit = FETCH_SIZE) }
            .getOrNull().orEmpty()
        val stubs = dtos.filter { it.width == width }.map { it.toStub() }
        if (stubs.isNotEmpty()) dao.insertStubs(stubs)
        fillVisibleSlots(width)
    }

    /** バッファから最大5件を表示スロットに昇格する */
    private suspend fun fillVisibleSlots(width: Int) {
        val needed = VISIBLE_SIZE - dao.countActive(width)
        if (needed > 0) {
            val ids = dao.getBufferIds(needed, width)
            if (ids.isNotEmpty()) dao.markVisible(ids)
        }
    }

    suspend fun getPuzzleForGame(id: Int): PuzzleEntity {
        val cached = dao.findById(id)
        val withDetail = if (cached != null && cached.pixelsJson.isNotEmpty()) {
            cached
        } else {
            val dto = api.getPuzzle(id)
            PuzzleEntity(
                id = dto.id,
                title = dto.title,
                theme = dto.theme,
                width = dto.width,
                height = dto.height,
                difficulty = dto.difficulty,
                difficultyLevel = dto.difficultyLevel,
                stageNumber = dto.stageNumber,
                pixelsJson = toJson(dto.pixels),
                paletteJson = toJson(dto.palette),
                paintedJson = cached?.paintedJson ?: "",
                isCleared = cached?.isCleared ?: false,
                isVisible = cached?.isVisible ?: false,
            ).also { dao.upsert(it) }
        }

        val userId = userIdManager.getOrCreate()
        val remoteProgress = runCatching { api.getProgress(id, userId) }
            .recoverCatching { error ->
                if (error is HttpException && error.code() == 404) null else throw error
            }
            .getOrNull()

        return if (remoteProgress != null && withDetail.paintedJson.isBlank()) {
            withDetail.copy(
                paintedJson = remoteProgress.paintedJson,
                isCleared = remoteProgress.isCleared,
            ).also { dao.upsert(it) }
        } else {
            withDetail
        }
    }

    suspend fun savePainted(id: Int, painted: List<List<Int>>) {
        dao.savePainted(id, toJson(painted))
    }

    suspend fun markCleared(id: Int, painted: List<List<Int>>) {
        val paintedJson = toJson(painted)
        dao.savePainted(id, paintedJson)
        dao.markCleared(id)

        // クリア後、同サイズの表示スロットを補充
        val width = dao.findById(id)?.width
        if (width != null) {
            if (dao.getBufferIds(1, width).isEmpty()) {
                // バッファ切れ → 次ページを追加取得
                val page = dao.countAll(width) / FETCH_SIZE
                val dtos = runCatching { api.getPuzzles(size = width, page = page, limit = FETCH_SIZE) }
                    .getOrNull().orEmpty()
                val stubs = dtos.filter { it.width == width }.map { it.toStub() }
                if (stubs.isNotEmpty()) dao.insertStubs(stubs)
            }
            fillVisibleSlots(width)
        }

        val userId = userIdManager.getOrCreate()
        runCatching {
            api.saveProgress(
                ProgressRequest(
                    userId = userId,
                    puzzleId = id,
                    paintedJson = paintedJson,
                    isCleared = true,
                ),
            )
        }
    }

    private fun PuzzleListItemDto.toStub() = PuzzleEntity(
        id = id,
        title = title,
        theme = theme,
        width = width,
        height = height,
        difficulty = difficulty,
        difficultyLevel = difficultyLevel,
        stageNumber = stageNumber,
        pixelsJson = "",
        paletteJson = "",
        isVisible = false,
    )

    private fun toJson(data: Any): String = gson.toJson(data)
}
