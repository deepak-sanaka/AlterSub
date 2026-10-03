package com.altersub.ui

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.altersub.R

/**
 * The app's UI font (AlterSub Sans), applied in code rather than through the theme. A theme font makes the
 * framework and AppCompat each build their own font collections, every one carrying Android's full system
 * fallback chain, which cost ~4 MB of native memory on API 28. Loading the family once and deriving each
 * weight from it keeps a single collection.
 */
object AppFont {

    private var family: Typeface? = null

    private fun family(context: Context): Typeface =
        family ?: context.applicationContext.resources.getFont(R.font.app_sans).also { family = it }

    /** Applies the font to every TextView (including Buttons) under [root], keeping each view's weight. */
    fun apply(root: View) = applyTo(root, family(root.context))

    private fun applyTo(view: View, base: Typeface) {
        when (view) {
            // The view's current (system) typeface already carries the weight its style asked for
            is TextView -> view.typeface = Typeface.create(base, view.typeface?.weight ?: 400, false)
            is ViewGroup -> for (i in 0 until view.childCount) applyTo(view.getChildAt(i), base)
        }
    }
}
