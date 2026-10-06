package com.altersub.core.model

import kotlin.math.roundToInt

/**
 * User-adjustable look of the overlay subtitles. Every change goes through the step/with* helpers,
 * which clamp to ranges that stay legible on a TV and reject unknown colours and backgrounds.
 */
data class SubtitleStyle(
    val textSizeSp: Float = DEFAULT_TEXT_SIZE_SP,
    val color: String = DEFAULT_COLOR,
    // Vertical centre of the subtitle block as a fraction of screen height (0 = top, 1 = bottom)
    val verticalPosition: Float = DEFAULT_VERTICAL_POSITION,
    val background: String = DEFAULT_BACKGROUND,
    // Horizontal centre of the subtitle block as a fraction of screen width (0 = left, 1 = right)
    val horizontalPosition: Float = DEFAULT_HORIZONTAL_POSITION
) {
    /** The box behind the text; [boxArgb] is null for none. */
    data class Background(val boxArgb: Int?)

    val colorArgb: Int get() = COLORS[color] ?: COLORS.getValue(DEFAULT_COLOR)

    /** The outline around the letters: white around black text, black around the rest. */
    val edgeArgb: Int get() = edgeFor(colorArgb)

    val boxArgb: Int? get() = (BACKGROUNDS[background] ?: BACKGROUNDS.getValue(DEFAULT_BACKGROUND)).boxArgb

    fun withTextSizeStep(steps: Int): SubtitleStyle =
        copy(textSizeSp = (textSizeSp + steps * TEXT_SIZE_STEP_SP).coerceIn(MIN_TEXT_SIZE_SP, MAX_TEXT_SIZE_SP))

    fun withPositionStep(steps: Int): SubtitleStyle =
        copy(verticalPosition = roundToHundredths(verticalPosition + steps * POSITION_STEP).coerceIn(MIN_POSITION, MAX_POSITION))

    fun withHorizontalStep(steps: Int): SubtitleStyle =
        copy(horizontalPosition = roundToHundredths(horizontalPosition + steps * HORIZONTAL_STEP).coerceIn(MIN_HORIZONTAL, MAX_HORIZONTAL))

    fun withColor(name: String): SubtitleStyle = if (name in COLORS) copy(color = name) else this

    fun withBackground(name: String): SubtitleStyle = if (name in BACKGROUNDS) copy(background = name) else this

    companion object {
        const val DEFAULT_TEXT_SIZE_SP = 30f
        const val TEXT_SIZE_STEP_SP = 2f
        const val MIN_TEXT_SIZE_SP = 16f
        const val MAX_TEXT_SIZE_SP = 60f

        const val DEFAULT_VERTICAL_POSITION = 0.82f
        // From the top edge to the bottom one; the overlay keeps the box on screen at either end
        const val POSITION_STEP = 0.04f
        const val MIN_POSITION = 0.05f
        const val MAX_POSITION = 0.95f

        const val DEFAULT_HORIZONTAL_POSITION = 0.5f
        const val HORIZONTAL_STEP = 0.05f
        const val MIN_HORIZONTAL = 0.1f
        const val MAX_HORIZONTAL = 0.9f

        private const val BLACK = 0xFF000000.toInt()
        private const val WHITE = 0xFFFFFFFF.toInt()

        const val DEFAULT_COLOR = "yellow"
        val COLORS: Map<String, Int> = linkedMapOf(
            "yellow" to 0xFFFFE500.toInt(), // High-visibility cinema yellow
            "white" to 0xFFFFFFFF.toInt(),
            "cyan" to 0xFF00E5FF.toInt(),
            "black" to BLACK // For the white backgrounds
        )

        fun edgeFor(textArgb: Int): Int = if (textArgb == BLACK) WHITE else BLACK

        // No box by default: the outline alone keeps the text readable
        const val DEFAULT_BACKGROUND = "none"
        val BACKGROUNDS: Map<String, Background> = linkedMapOf(
            "none" to Background(boxArgb = null),
            "translucent-black" to Background(boxArgb = 0xB3000000.toInt()),
            "black" to Background(boxArgb = BLACK),
            "translucent-white" to Background(boxArgb = 0xB3FFFFFF.toInt()),
            "white" to Background(boxArgb = WHITE)
        )

        private fun roundToHundredths(value: Float): Float = (value * 100).roundToInt() / 100f
    }
}
