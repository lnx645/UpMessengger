package com.upme.messenger.presentation.common

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.res.ResourcesCompat
import com.upme.messenger.R

/**
 * Reusable settings row — Material3, Manrope, clean.
 *
 * Menggantikan LinearLayout 56dp manual yang berulang di fragment_profil.xml.
 * Struktur: [36dp icon box] + [title + optional subtitle] + [trailing: chevron / value text / custom]
 *
 * Ikon: 36dp rounded 10dp, background warna kategori (biru/ungu/hijau/orange/merah).
 * Teks: Manrope Medium 15sp title, Manrope Regular 12sp subtitle #667781.
 * Trailing: chevron 18dp default, atau value text via [trailingText].
 *
 * Usage:
 *   val item = SettingItem(context).apply {
 *       iconRes = R.drawable.ic_bell
 *       iconBgColor = 0xFFE53935.toInt() // or use IconBg.* presets
 *       title = "Notifikasi"
 *       subtitle = "Bunyi, getar"
 *       onClick = { findNavController().navigate(...) }
 *   }
 *   section.addItem(item)
 */
class SettingItem @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val iconBox: FrameLayout
    private val iconView: ImageView
    private val txtTitle: TextView
    private val txtSubtitle: TextView
    private val trailingTextView: TextView
    private val chevronView: ImageView

    var title: String
        get() = txtTitle.text.toString()
        set(value) { txtTitle.text = value }

    var subtitle: String?
        get() = txtSubtitle.text.toString().takeIf { txtSubtitle.visibility == VISIBLE }
        set(value) {
            if (value.isNullOrBlank()) {
                txtSubtitle.visibility = GONE
                txtSubtitle.text = ""
            } else {
                txtSubtitle.text = value
                txtSubtitle.visibility = VISIBLE
            }
        }

    /** Text di kanan sebelum chevron, mis. "ID", "Aktif". Sembunyikan chevron bila ingin value-only via [showChevron]. */
    var trailingText: String?
        get() = trailingTextView.text.toString().takeIf { trailingTextView.visibility == VISIBLE }
        set(value) {
            if (value.isNullOrBlank()) {
                trailingTextView.visibility = GONE
            } else {
                trailingTextView.text = value
                trailingTextView.visibility = VISIBLE
            }
        }

    var showChevron: Boolean
        get() = chevronView.visibility == VISIBLE
        set(value) { chevronView.visibility = if (value) VISIBLE else GONE }

    @DrawableRes
    var iconRes: Int = 0
        set(value) {
            field = value
            if (value != 0) iconView.setImageResource(value)
        }

    @ColorInt
    var iconBgColor: Int = 0xFF2196F3.toInt()
        set(value) {
            field = value
            updateIconBg()
        }

    var onClick: (() -> Unit)? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(56)
        setPadding(dp(16), 0, dp(16), 0)
        isClickable = true
        isFocusable = true
        // Ripple foreground — Material selectable
        val outValue = android.util.TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
        foreground = ResourcesCompat.getDrawable(resources, outValue.resourceId, context.theme)

        val manrope = ResourcesCompat.getFont(context, R.font.manrope_regular)
        val manropeMedium = ResourcesCompat.getFont(context, R.font.manrope_medium)

        // Icon box 36dp, rounded 10dp
        iconBox = FrameLayout(context).apply {
            layoutParams = LayoutParams(dp(36), dp(36))
            updateIconBg()
        }
        iconView = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER)
        }
        iconBox.addView(iconView)

        val textContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(14)
            }
        }

        txtTitle = TextView(context).apply {
            textSize = 15f
            setTextColor(0xFF111B21.toInt())
            typeface = manropeMedium ?: manrope
            maxLines = 1
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        txtSubtitle = TextView(context).apply {
            textSize = 12f
            setTextColor(0xFF667781.toInt())
            typeface = manrope
            maxLines = 1
            visibility = GONE
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(2)
            }
        }
        textContainer.addView(txtTitle)
        textContainer.addView(txtSubtitle)

        trailingTextView = TextView(context).apply {
            textSize = 13f
            setTextColor(0xFF667781.toInt())
            typeface = manrope
            maxLines = 1
            visibility = GONE
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(8)
            }
        }

        chevronView = ImageView(context).apply {
            layoutParams = LayoutParams(dp(18), dp(18)).apply { marginStart = dp(8) }
            setImageResource(R.drawable.ic_chevron)
            alpha = 0.9f
        }

        addView(iconBox)
        addView(textContainer)
        addView(trailingTextView)
        addView(chevronView)

        super.setOnClickListener {
            if (!ClickGuard.canClick(350)) return@setOnClickListener
            onClick?.invoke()
        }
    }

    override fun setOnClickListener(l: OnClickListener?) {
        // Preserve internal debounce; wrap external listener
        onClick = { l?.onClick(this) }
    }

    private fun updateIconBg() {
        val d = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(10).toFloat()
            setColor(iconBgColor)
        }
        iconBox.background = d
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    /** Preset warna icon selaras drawable bg_icon_* dan colors.xml. */
    object IconBg {
        val Blue = 0xFF2196F3.toInt()
        val Purple = 0xFF9C27B0.toInt()
        val Green = 0xFF25D366.toInt()
        val GreenDark = 0xFF075E54.toInt()
        val Orange = 0xFFFF9800.toInt()
        val Red = 0xFFE53935.toInt()
    }
}
