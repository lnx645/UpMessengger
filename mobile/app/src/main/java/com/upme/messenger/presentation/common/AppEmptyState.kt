package com.upme.messenger.presentation.common

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.res.ResourcesCompat
import com.upme.messenger.R
import com.google.android.material.button.MaterialButton

/**
 * Reusable empty-state — icon + title + subtitle + CTA.
 * Dipakai di fragment_teman vs fragment_chats biar konsisten (U8).
 *
 * Usage:
 *   val empty = AppEmptyState(context).apply {
 *     iconRes = R.drawable.ic_empty_teman
 *     title = "Belum ada teman"
 *     subtitle = "Invite via PIN untuk mulai chat"
 *     ctaText = "Invite Teman"
 *     onCtaClick = { showInviteSheet() }
 *   }
 *   container.addView(empty)
 */
class AppEmptyState @JvmOverloads constructor(
  context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

  private val img: ImageView
  private val txtTitle: TextView
  private val txtSubtitle: TextView
  private val btnCta: MaterialButton

  var iconRes: Int = 0
    set(v) { field = v; if (v != 0) { img.setImageResource(v); img.visibility = VISIBLE } else img.visibility = GONE }

  var title: String
    get() = txtTitle.text.toString()
    set(v) { txtTitle.text = v; txtTitle.visibility = if (v.isBlank()) GONE else VISIBLE }

  var subtitle: String?
    get() = txtSubtitle.text.toString().takeIf { txtSubtitle.visibility == VISIBLE }
    set(v) { if (v.isNullOrBlank()) { txtSubtitle.visibility = GONE } else { txtSubtitle.text = v; txtSubtitle.visibility = VISIBLE } }

  var ctaText: String?
    get() = btnCta.text.toString().takeIf { btnCta.visibility == VISIBLE }
    set(v) { if (v.isNullOrBlank()) btnCta.visibility = GONE else { btnCta.text = v; btnCta.visibility = VISIBLE } }

  var onCtaClick: (() -> Unit)? = null

  init {
    orientation = VERTICAL
    gravity = Gravity.CENTER
    setPadding(dp(24), dp(24), dp(24), dp(24))
    val manrope = ResourcesCompat.getFont(context, R.font.manrope_regular)
    val manropeMed = ResourcesCompat.getFont(context, R.font.manrope_medium)
    img = ImageView(context).apply {
      layoutParams = LayoutParams(dp(80), dp(80)).apply { bottomMargin = dp(16) }
      alpha = 0.7f
      visibility = GONE
    }
    txtTitle = TextView(context).apply {
      textSize = 16f
      typeface = manropeMed ?: manrope
      setTextColor(0xFF111B21.toInt())
      gravity = Gravity.CENTER
      layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }
    txtSubtitle = TextView(context).apply {
      textSize = 13f
      typeface = manrope
      setTextColor(0xFF667781.toInt())
      gravity = Gravity.CENTER
      visibility = GONE
      layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) }
    }
    btnCta = MaterialButton(context).apply {
      visibility = GONE
      cornerRadius = dp(18)
      layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16) }
      setOnClickListener { onCtaClick?.invoke() }
    }
    // apply style
    addView(img); addView(txtTitle); addView(txtSubtitle); addView(btnCta)
  }
}
