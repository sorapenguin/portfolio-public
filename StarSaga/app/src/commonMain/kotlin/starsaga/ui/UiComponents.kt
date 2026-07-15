package starsaga.ui

import korlibs.image.color.Colors
import korlibs.image.color.RGBA
import korlibs.korge.view.Container
import korlibs.korge.view.Text
import korlibs.korge.view.View
import korlibs.korge.view.position
import korlibs.korge.view.solidRect
import korlibs.korge.view.text

object UiTextStyle {
    const val TitleSize = 20.0
    const val BodySize = 14.0
    const val ButtonSize = 16.0
    const val DebugSize = 11.0

    // Noto Sans JP appears visually low when placed by its baseline in KorGE Text.
    const val BodyTextVerticalCorrection = 2.0
    const val ButtonLabelVerticalCorrection = 10.0
}

data class UiPanelLayout(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val padding: Double = 18.0,
    val footerHeight: Double = 48.0,
) {
    val titleY: Double get() = y + padding
    val contentTop: Double get() = y + padding + 36.0
    val contentBottom: Double get() = y + height - footerHeight - padding
    val footerTop: Double get() = y + height - footerHeight
}

data class UiButton(
    val background: View,
    val label: Text,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
) {
    fun setVisible(value: Boolean) {
        background.visible = value
        label.visible = value
    }

    fun contains(px: Double, py: Double): Boolean =
        px >= x && px <= x + width && py >= y && py <= y + height
}

fun Container.createUiText(
    label: String,
    x: Double,
    y: Double,
    textSize: Double = UiTextStyle.BodySize,
    color: RGBA = Colors.WHITE,
    verticalCorrection: Double = UiTextStyle.BodyTextVerticalCorrection,
): Text = text(
    label,
    textSize = textSize,
    color = color,
    font = StarSagaFonts.font,
) {
    position(x, y - verticalCorrection)
}

fun Container.createUiButton(
    x: Double,
    y: Double,
    width: Double,
    height: Double,
    label: String,
    background: RGBA,
    textColor: RGBA = Colors.WHITE,
    fontSize: Double = UiTextStyle.ButtonSize,
    textX: Double = x + 12.0,
): UiButton {
    val rect = solidRect(width, height, background) {
        position(x, y)
    }
    val labelText = createUiText(
        label = label,
        x = textX,
        y = centeredButtonTextY(y, height, fontSize),
        textSize = fontSize,
        color = textColor,
        verticalCorrection = 0.0,
    )
    return UiButton(rect, labelText, x, y, width, height)
}

fun centeredButtonTextY(buttonY: Double, buttonHeight: Double, fontSize: Double): Double =
    buttonY + (buttonHeight - fontSize) / 2.0 - UiTextStyle.ButtonLabelVerticalCorrection
