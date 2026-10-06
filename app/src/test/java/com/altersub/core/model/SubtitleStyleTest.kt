package com.altersub.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleStyleTest {

    @Test
    fun testTextSizeStepsAreClamped() {
        val style = SubtitleStyle()
        assertEquals(32f, style.withTextSizeStep(1).textSizeSp)
        assertEquals(26f, style.withTextSizeStep(-2).textSizeSp)
        assertEquals(SubtitleStyle.MAX_TEXT_SIZE_SP, style.withTextSizeStep(1_000).textSizeSp)
        assertEquals(SubtitleStyle.MIN_TEXT_SIZE_SP, style.withTextSizeStep(-1_000).textSizeSp)
    }

    @Test
    fun testPositionStepsAreRoundedAndClamped() {
        var style = SubtitleStyle()
        repeat(3) { style = style.withPositionStep(-1) }
        assertEquals(0.70f, style.verticalPosition) // No float drift from repeated steps
        assertEquals(SubtitleStyle.MAX_POSITION, SubtitleStyle().withPositionStep(50).verticalPosition)
        // All the way up, as far as all the way down
        assertEquals(0.05f, SubtitleStyle().withPositionStep(-50).verticalPosition)
        assertEquals(1f - SubtitleStyle.MAX_POSITION, SubtitleStyle.MIN_POSITION, 0.0001f)
    }

    @Test
    fun testSideStepsAreRoundedAndClamped() {
        var style = SubtitleStyle()
        assertEquals(0.5f, style.horizontalPosition)
        repeat(3) { style = style.withHorizontalStep(-1) }
        assertEquals(0.35f, style.horizontalPosition)
        assertEquals(SubtitleStyle.MAX_HORIZONTAL, SubtitleStyle().withHorizontalStep(50).horizontalPosition)
        assertEquals(SubtitleStyle.MIN_HORIZONTAL, SubtitleStyle().withHorizontalStep(-50).horizontalPosition)
    }

    @Test
    fun testOnlyPaletteColorsAreAccepted() {
        val white = SubtitleStyle().withColor("white")
        assertEquals("white", white.color)
        assertEquals(0xFFFFFFFF.toInt(), white.colorArgb)

        // Unknown names (or injected markup) leave the style unchanged
        assertEquals("white", white.withColor("<script>").color)
        assertEquals(SubtitleStyle.COLORS.getValue("yellow"), SubtitleStyle(color = "bogus").colorArgb)
    }

    @Test
    fun testBackgrounds() {
        // No box by default: the chosen colour, outlined in black
        val default = SubtitleStyle(color = "cyan")
        assertEquals("none", default.background)
        assertNull(default.boxArgb)
        assertEquals(0xFF000000.toInt(), default.edgeArgb)

        assertEquals(0xB3000000.toInt(), default.withBackground("translucent-black").boxArgb)
        assertEquals(0xFF000000.toInt(), default.withBackground("black").boxArgb)
        assertEquals(0xB3FFFFFF.toInt(), default.withBackground("translucent-white").boxArgb)

        // A white box keeps the chosen colour; black text is one of the choices, and gets a white outline
        val white = default.withBackground("white")
        assertEquals(0xFFFFFFFF.toInt(), white.boxArgb)
        assertEquals(SubtitleStyle.COLORS.getValue("cyan"), white.colorArgb)
        val blackOnWhite = white.withColor("black")
        assertEquals(0xFF000000.toInt(), blackOnWhite.colorArgb)
        assertEquals(0xFFFFFFFF.toInt(), blackOnWhite.edgeArgb)

        // Unknown names are ignored, and an unknown stored one falls back to the default
        assertEquals("white", white.withBackground("<script>").background)
        assertNull(SubtitleStyle(background = "bogus").boxArgb)
    }
}
