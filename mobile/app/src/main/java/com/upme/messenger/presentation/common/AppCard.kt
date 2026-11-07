package com.upme.messenger.presentation.common

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.card.MaterialCardView

/**
 * Reusable card container — Material3, Manrope-ready, clean.
 *
 * Default: corner 16dp, elevation 0dp, stroke 0dp, background white,
 * dipakai konsisten menggantikan MaterialCardView manual di fragment_profil / pin / dll.
 *
 * Usage XML:
 *   <com.upme.messenger.presentation.common.AppCard
 *       android:layout_width="match_parent"
 *       android:layout_height="wrap_content">
 *       <!-- content -->
 *   </com.upme.messenger.presentation.common.AppCard>
 *
 * Usage code:
 *   val card = AppCard(context).apply { radius = dp(20f) }
 */
class AppCard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialCardViewStyle
) : MaterialCardView(context, attrs, defStyleAttr) {

    init {
        // Only apply defaults when not overridden via XML attrs
        val hasRadius = attrs?.getAttributeValue(
            "http://schemas.android.com/apk/res-auto", "cardCornerRadius"
        ) != null
        val hasElevation = attrs?.getAttributeValue(
            "http://schemas.android.com/apk/res-auto", "cardElevation"
        ) != null

        if (!hasRadius) radius = dp(16f)
        if (!hasElevation) cardElevation = 0f
        if (strokeWidth == 0) strokeWidth = 0
        // keep cardBackgroundColor as set in XML or default white
        // default white if no background specified via XML; MaterialCardView default is surface color
        // keep as-is — caller can override via app:cardBackgroundColor
    }

    /** Helper — 20dp variant for header cards (avatar, profil header). */
    fun asHeaderCard() {
        radius = dp(20f)
    }

    /** Helper — 12dp compact variant. */
    fun asCompactCard() {
        radius = dp(12f)
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
