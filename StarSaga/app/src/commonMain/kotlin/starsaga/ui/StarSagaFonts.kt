package starsaga.ui

import korlibs.image.font.DefaultTtfFont
import korlibs.image.font.Font
import korlibs.image.font.readTtfFont
import korlibs.io.file.std.resourcesVfs

object StarSagaFonts {
    var font: Font = DefaultTtfFont
        private set

    suspend fun load() {
        font = try {
            resourcesVfs["fonts/NotoSansJP.ttf"].readTtfFont()
        } catch (_: Throwable) {
            DefaultTtfFont
        }
    }
}
