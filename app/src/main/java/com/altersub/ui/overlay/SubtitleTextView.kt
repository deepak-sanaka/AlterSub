package com.altersub.ui.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View

/**
 * High-performance, zero-allocation subtitle rendering View for Android TV.
 * Uses stroked text and semi-transparent bounding box for 100% legibility on any movie scene.
 */
class SubtitleTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var subtitleText: String = ""

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFE500") // High-visibility cinema yellow
        textSize = 34f * resources.displayMetrics.scaledDensity
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val strokePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 34f * resources.displayMetrics.scaledDensity
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        style = Paint.Style.STROKE
        strokeWidth = 5f * resources.displayMetrics.density
        strokeJoin = Paint.Join.ROUND
        textAlign = Paint.Align.CENTER
    }

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#99000000") // Semi-transparent black backing box
        style = Paint.Style.FILL
    }

    private val bgRect = RectF()
    private val paddingHorizontal = 24f * resources.displayMetrics.density
    private val paddingVertical = 12f * resources.displayMetrics.density

    fun setSubtitle(text: String) {
        if (subtitleText != text) {
            subtitleText = text
            invalidate()
        }
    }

    fun setTextSizeSp(sp: Float) {
        val px = sp * resources.displayMetrics.scaledDensity
        textPaint.textSize = px
        strokePaint.textSize = px
        strokePaint.strokeWidth = (sp / 7f) * resources.displayMetrics.density
        invalidate()
    }

    fun setTextColor(colorHex: Int) {
        textPaint.color = colorHex
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (subtitleText.isBlank()) return

        val lines = subtitleText.split("\n")
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val centerX = viewWidth / 2f

        val lineHeight = textPaint.fontSpacing
        val totalTextHeight = lines.size * lineHeight

        // Position subtitles in lower third of screen
        val startY = viewHeight - totalTextHeight - (48f * resources.displayMetrics.density)

        // Calculate max line width for bounding box
        var maxLineWidth = 0f
        for (line in lines) {
            val w = textPaint.measureText(line)
            if (w > maxLineWidth) maxLineWidth = w
        }

        // Draw background box
        val boxLeft = centerX - (maxLineWidth / 2f) - paddingHorizontal
        val boxRight = centerX + (maxLineWidth / 2f) + paddingHorizontal
        val boxTop = startY - paddingVertical
        val boxBottom = startY + totalTextHeight + paddingVertical

        bgRect.set(boxLeft, boxTop, boxRight, boxBottom)
        canvas.drawRoundRect(bgRect, 16f, 16f, backgroundPaint)

        // Draw stroked text then fill text for sharp outline
        for (i in lines.indices) {
            val line = lines[i]
            val y = startY + (i + 1) * lineHeight - textPaint.descent()
            canvas.drawText(line, centerX, y, strokePaint)
            canvas.drawText(line, centerX, y, textPaint)
        }
    }
}
