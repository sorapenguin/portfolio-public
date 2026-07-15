package starsaga.ui

import korlibs.image.bitmap.Bitmap
import korlibs.image.format.readBitmap
import korlibs.io.file.std.resourcesVfs

object CreatureImages {
    private val cache = mutableMapOf<Int, Bitmap>()

    // StarSaga creatureId → resources/characters/ の PNG ファイル名
    // T1の5体: sf_char_c_1{role}.png
    // T1ボス(id=1001): T7 DEFN 画像を流用（強さの差を視覚化）
    private val idToPath = mapOf(
        1 to "characters/sf_char_c_1atck.png",
        2 to "characters/sf_char_c_1defn.png",
        3 to "characters/sf_char_c_1area.png",
        4 to "characters/sf_char_c_1heal.png",
        5 to "characters/sf_char_c_1luck.png",
        1001 to "characters/sf_char_c_7defn.png",
    )

    suspend fun load() {
        for ((id, path) in idToPath) {
            try {
                cache[id] = resourcesVfs[path].readBitmap()
            } catch (_: Throwable) {
                // ロード失敗時は solidRect フォールバックで表示
            }
        }
    }

    fun get(creatureId: Int): Bitmap? = cache[creatureId]
}
