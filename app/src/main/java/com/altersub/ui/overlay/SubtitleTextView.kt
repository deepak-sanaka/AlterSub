package com.altersub.ui.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View

/**
 * High-performance, zero-allocation subtitle rendering View for Android TV and mobile.
 * Uses stroked text and semi-transparent bounding box for 100% legibility on any movie scene.
 * Automatically fits text within safe screen margins to prevent clipping.
 */
class SubtitleTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var subtitleText: String = ""
    // Split once per subtitle change; onDraw iterates this by index so drawing allocates nothing (AGENTS.md Rule 2)
    private var lines: Array<String> = emptyArray()
    private var baseTextSizePx: Float = 30f * resources.displayMetrics.scaledDensity
    // Centre of the subtitle block as fractions of the view's height and width
    private var verticalPosition: Float = 0.82f
    private var horizontalPosition: Float = 0.5f

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFE500") // High-visibility cinema yellow
        textSize = baseTextSizePx
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val strokePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = baseTextSizePx
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        style = Paint.Style.STROKE
        strokeWidth = 5f * resources.displayMetrics.density
        strokeJoin = Paint.Join.ROUND
        textAlign = Paint.Align.CENTER
    }

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B3000000") // High-contrast semi-transparent black backing box
        style = Paint.Style.FILL
    }
    private var drawBox = true

    private val bgRect = RectF()
    private val paddingHorizontal = 20f * resources.displayMetrics.density
    private val paddingVertical = 10f * resources.displayMetrics.density

    fun setSubtitle(text: String) {
        if (subtitleText != text) {
            subtitleText = text
            lines = if (text.isBlank()) emptyArray() else text.split('\n').toTypedArray()
            invalidate()
        }
    }

    fun setTextSizeSp(sp: Float) {
        baseTextSizePx = sp * resources.displayMetrics.scaledDensity
        invalidate()
    }

    fun setTextColor(colorHex: Int) {
        textPaint.color = colorHex
        invalidate()
    }

    /** The box behind the text ([boxArgb] null for none) and the colour of the letters' outline. */
    fun setBackground(boxArgb: Int?, edgeArgb: Int) {
        drawBox = boxArgb != null
        if (boxArgb != null) backgroundPaint.color = boxArgb
        strokePaint.color = edgeArgb
        invalidate()
    }

    fun setVerticalPosition(fraction: Float) {
        verticalPosition = fraction
        invalidate()
    }

    fun setHorizontalPosition(fraction: Float) {
        horizontalPosition = fraction
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (lines.isEmpty()) return

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        // Ensure text fits within 90% of screen width
        val maxAvailableWidth = viewWidth * 0.90f
        var maxLineWidth = 0f
        textPaint.textSize = baseTextSizePx

        for (i in lines.indices) {
            val w = textPaint.measureText(lines[i])
            if (w > maxLineWidth) maxLineWidth = w
        }

        val scale = if (maxLineWidth > maxAvailableWidth && maxLineWidth > 0f) {
            maxAvailableWidth / maxLineWidth
        } else {
            1.0f
        }

        val appliedTextSize = baseTextSizePx * scale
        textPaint.textSize = appliedTextSize
        strokePaint.textSize = appliedTextSize
        strokePaint.strokeWidth = (appliedTextSize / 7f)

        val lineHeight = textPaint.fontSpacing
        val totalTextHeight = lines.size * lineHeight

        // Measure scaled width for bounding box
        var scaledMaxWidth = 0f
        for (i in lines.indices) {
            val w = textPaint.measureText(lines[i])
            if (w > scaledMaxWidth) scaledMaxWidth = w
        }

        // Centre the block where the user put it (default 82% down, in the middle), but keep the whole box inside
        // the TV's safe margins, so the top, bottom and side positions never cut it off
        val marginY = viewHeight * SAFE_MARGIN + paddingVertical
        val startY = clampCentred(viewHeight * verticalPosition - totalTextHeight / 2f, marginY, viewHeight - marginY - totalTextHeight)
        val halfBox = scaledMaxWidth / 2f + paddingHorizontal
        val marginX = viewWidth * SAFE_MARGIN + halfBox
        val centerX = clampCentred(viewWidth * horizontalPosition, marginX, viewWidth - marginX)

        val boxLeft = centerX - (scaledMaxWidth / 2f) - paddingHorizontal
        val boxRight = centerX + (scaledMaxWidth / 2f) + paddingHorizontal
        val boxTop = startY - paddingVertical
        val boxBottom = startY + totalTextHeight + paddingVertical

        if (drawBox) {
            bgRect.set(boxLeft, boxTop, boxRight, boxBottom)
            canvas.drawRoundRect(bgRect, 18f, 18f, backgroundPaint)
        }

        // Draw stroked text, then fill text for sharp outline
        for (i in lines.indices) {
            val line = lines[i]
            val y = startY + (i + 1) * lineHeight - textPaint.descent()
            canvas.drawText(line, centerX, y, strokePaint)
            canvas.drawText(line, centerX, y, textPaint)
        }
    }

    /** [value] kept within [min]..[max], or halfway between them when the block is too big to fit at all. */
    private fun clampCentred(value: Float, min: Float, max: Float): Float =
        if (max < min) (min + max) / 2f else value.coerceIn(min, max)

    private companion object {
        // Kept clear on every edge: the area some TVs crop (overscan)
        const val SAFE_MARGIN = 0.03f
    }
}
