package com.altersub.core.model

import kotlin.math.roundToInt

/**
 * User-adjustable look of the overlay subtitles. Every change goes through the step/with* helpers,
 * which clamp to ranges that stay legible on a TV and reject unknown colours.
 */
data class SubtitleStyle(
    val textSizeSp: Float = DEFAULT_TEXT_SIZE_SP,
    val color: String = DEFAULT_COLOR,
    // Vertical centre of the subtitle block as a fraction of screen height (0 = top, 1 = bottom)
    val verticalPosition: Float = DEFAULT_VERTICAL_POSITION
) {
    val colorArgb: Int get() = COLORS[color] ?: COLORS.getValue(DEFAULT_COLOR)

    fun withTextSizeStep(steps: Int): SubtitleStyle =
        copy(textSizeSp = (textSizeSp + steps * TEXT_SIZE_STEP_SP).coerceIn(MIN_TEXT_SIZE_SP, MAX_TEXT_SIZE_SP))

    fun withPositionStep(steps: Int): SubtitleStyle =
        copy(verticalPosition = roundToHundredths(verticalPosition + steps * POSITION_STEP).coerceIn(MIN_POSITION, MAX_POSITION))

    fun withColor(name: String): SubtitleStyle = if (name in COLORS) copy(color = name) else this

    companion object {
        const val DEFAULT_TEXT_SIZE_SP = 30f
        const val TEXT_SIZE_STEP_SP = 2f
        const val MIN_TEXT_SIZE_SP = 16f
        const val MAX_TEXT_SIZE_SP = 60f

        const val DEFAULT_VERTICAL_POSITION = 0.82f
        const val POSITION_STEP = 0.02f
        const val MIN_POSITION = 0.5f
        const val MAX_POSITION = 0.95f

        const val DEFAULT_COLOR = "yellow"
        val COLORS: Map<String, Int> = linkedMapOf(
            "yellow" to 0xFFFFE500.toInt(), // High-visibility cinema yellow
            "white" to 0xFFFFFFFF.toInt(),
            "cyan" to 0xFF00E5FF.toInt()
        )

        private fun roundToHundredths(value: Float): Float = (value * 100).roundToInt() / 100f
    }
}
