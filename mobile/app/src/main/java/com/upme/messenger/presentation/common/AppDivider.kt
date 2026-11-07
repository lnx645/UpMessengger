package com.upme.messenger.presentation.common

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout

/**
 * Reusable thin divider — pengganti View 0.7dp manual yang tersebar di layout.
 *
 * Default: height 0.7dp, color #0F000000 (divider tipis WA-style),
 * full width. Varian inset 66dp untuk list Settings (sejajar teks, bukan icon).
 *
 * Usage XML:
 *   <com.upme.messenger.presentation.common.AppDivider
 *       android:layout_width="match_parent"
 *       android:layout_height="wrap_content" />
 *
 *   <!-- inset variant -->
 *   <com.upme.messenger.presentation.common.AppDivider
 *       android:layout_width="match_parent"
 *       android:layout_height="wrap_content"
 *       app:insetStart="66dp" />
 */
class AppDivider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var insetStartDp: Float = 0f
        set(value) {
            field = value
            updateLayoutParams()
        }

    init {
        setBackgroundColor(0x0F000000)
        // default thin height
        val h = (0.7f * resources.displayMetrics.density).toInt().coerceAtLeast(1)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, h
        )

        // read optional inset from attrs if provided
        attrs?.let {
            // Support app:insetStart without requiring attrs.xml declaration:
            // fallback via raw attribute lookup
            val inset = it.getAttributeValue(
                "http://schemas.android.com/apk/res-auto", "insetStart"
            )
            inset?.let { raw ->
                // raw may be "66dp" or dimension; parse best-effort
                val dm = resources.displayMetrics.density
                when {
                    raw.endsWith("dp") -> insetStartDp = raw.removeSuffix("dp").toFloatOrNull() ?: 0f
                    else -> {
                        // Try as dimension resource id
                        val resId = it.getAttributeResourceValue(
                            "http://schemas.android.com/apk/res-auto", "insetStart", 0
                        )
                        if (resId != 0) {
                            insetStartDp = resources.getDimension(resId) / dm
                            updateLayoutParams()
                        }
                    }
                }
            }
        }
        if (insetStartDp > 0f) updateLayoutParams()
    }

    private fun updateLayoutParams() {
        val h = (0.7f * resources.displayMetrics.density).toInt().coerceAtLeast(1)
        val insetPx = (insetStartDp * resources.displayMetrics.density).toInt()
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, h
        ).apply {
            if (insetPx > 0) marginStart = insetPx
        }
    }

    companion object {
        /** Inset default 66dp untuk Settings list (align dengan teks, skip icon 36dp+14dp+16dp). */
        const val INSET_SETTINGS_DP = 66f
    }
}
