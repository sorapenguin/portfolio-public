package islanddev.ui

import korlibs.image.color.RGBA
import korlibs.image.font.DefaultTtfFont
import korlibs.image.font.Font
import korlibs.image.font.readTtfFont
import korlibs.io.file.std.resourcesVfs
import korlibs.korge.input.onClick
import korlibs.korge.view.Container
import korlibs.korge.view.Text
import korlibs.korge.view.container
import korlibs.korge.view.position
import korlibs.korge.view.text

internal object IslandUiFonts {
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

internal fun Container.actionButton(
    label: String,
    x: Double,
    y: Double,
    width: Double,
    height: Double,
    enabled: Boolean = true,
    onClick: () -> Unit
): Container = container {
    position(x, y)
    val fill = when {
        !enabled -> IslandTheme.Color.ButtonDisabled
        label == "挑戦する" || label == "AUTO: ON" -> IslandTheme.Color.ButtonImportant
        else -> IslandTheme.Color.Button
    }
    panelRect(width, height, fill = fill, border = IslandTheme.Color.ButtonBorder, borderSize = 2.0)
    val textSize = if (label.length <= 2) 20.0 else 15.0
    text(
        label,
        textSize = textSize,
        color = if (enabled) IslandTheme.Color.Text else IslandTheme.Color.MutedText,
        font = IslandUiFonts.font
    ) {
        val estimatedWidth = label.sumOf { if (it.code > 127) 1.0 else 0.62 } * textSize
        position(((width - estimatedWidth) / 2.0).coerceAtLeast(6.0), ((height - textSize) / 2.0) - 1.0)
    }
    if (enabled) {
        onClick { onClick() }
    }
}

internal fun Container.infoButton(
    title: String,
    detail: String,
    x: Double,
    y: Double,
    width: Double,
    height: Double,
    enabled: Boolean,
    clickableWhenDisabled: Boolean = false,
    onClick: () -> Unit
): Container = container {
    position(x, y)
    panelRect(
        width,
        height,
        fill = if (enabled) RGBA(31, 76, 112, 255) else IslandTheme.Color.ButtonDisabled,
        border = if (enabled) IslandTheme.Color.ButtonBorder else RGBA(76, 88, 102, 255),
        borderSize = 2.0
    )
    text(
        title,
        textSize = 14,
        color = if (enabled) IslandTheme.Color.Text else IslandTheme.Color.MutedText,
        font = IslandUiFonts.font
    ) {
        position(12, 8)
    }
    text(
        detail,
        textSize = 10,
        color = if (enabled) RGBA(214, 230, 246, 255) else RGBA(140, 153, 168, 255),
        font = IslandUiFonts.font
    ) {
        position(12, 34)
    }
    if (enabled || clickableWhenDisabled) onClick { onClick() }
}

internal fun Container.label(
    value: String,
    x: Double,
    y: Double,
    size: Double = 16.0,
    color: RGBA = IslandTheme.Color.Text
): Text = text(value, textSize = size, color = color, font = IslandUiFonts.font) {
    position(x, y)
}

internal fun formatDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val secs = safe % 60
    return if (hours > 0) {
        "${hours}:${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
    } else {
        "${minutes}:${secs.toString().padStart(2, '0')}"
    }
}

internal fun formatApproxDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    if (safe < 60) return "あと1分未満"
    val minutes = (safe + 59) / 60
    if (minutes < 60) return "あと約${minutes}分"
    val hours = (minutes + 59) / 60
    return "あと約${hours}時間"
}
