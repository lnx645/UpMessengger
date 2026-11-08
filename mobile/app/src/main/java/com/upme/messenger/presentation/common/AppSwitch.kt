package com.upme.messenger.presentation.common

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.materialswitch.MaterialSwitch
import com.upme.messenger.R

/**
 * Reusable switch row — Material3, Manrope, clean.
 *
 * Baris setting dengan title + optional subtitle + MaterialSwitch di kanan.
 * Warna track/thumb mengikuti upme_green_dark (#075E54) selaras themes.xml.
 * Ripple & debounce per-view via [debounceClick].
 *
 * Usage code:
 *   val row = AppSwitch(context).apply {
 *       title = "Notifikasi"
 *       subtitle = "Bunyi & getar"
 *       isChecked = true
 *       onCheckedChange = { checked -> vm.setNotif(checked) }
 *   }
 *   container.addView(row)
 *
 * Usage XML (programmatic preferred; XML attrs optional):
 *   <com.upme.messenger.presentation.common.AppSwitch
 *       android:layout_width="match_parent"
 *       android:layout_height="wrap_content" />
 */
class AppSwitch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val txtTitle: TextView
    private val txtSubtitle: TextView
    val switch: MaterialSwitch

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

    var isChecked: Boolean
        get() = switch.isChecked
        set(value) { switch.isChecked = value }

    var onCheckedChange: ((Boolean) -> Unit)? = null
    private var suppressCallback = false

    /** Set checked tanpa memicu [onCheckedChange] — untuk pull/sync awal. */
    fun setCheckedSilent(checked: Boolean) {
        suppressCallback = true
        switch.isChecked = checked
        suppressCallback = false
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val padH = resources.dp(16)
        val padV = resources.dp(12)
        setPadding(padH, padV, padH, padV)
        isClickable = true
        isFocusable = true
        background = ResourcesCompat.getDrawable(
            resources, android.R.drawable.list_selector_background, context.theme
        )
        // foreground ripple on API 23+
        foreground = ResourcesCompat.getDrawable(
            resources, android.R.drawable.list_selector_background, context.theme
        )

        val manrope = FontCacheShared.regular(context)
        val manropeMedium = FontCacheShared.medium(context)

        val textContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
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
            maxLines = 2
            visibility = GONE
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = resources.dp(2)
            }
        }

        textContainer.addView(txtTitle)
        textContainer.addView(txtSubtitle)

        switch = MaterialSwitch(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = resources.dp(16)
            }
            // Material3 switch colors are themed via colorPrimary; keep default from Theme.UpMessenger
            isClickable = true
            isFocusable = true
        }

        addView(textContainer)
        addView(switch)

        // Whole row click toggles switch (debounced per-view)
        debounceClick(300) { switch.toggle() }
        switch.setOnCheckedChangeListener { _, checked ->
            if (!suppressCallback) onCheckedChange?.invoke(checked)
        }
    }

    /** Programmatic factory for quick building inside Settings. */
    companion object {
        fun create(
            context: Context,
            title: String,
            subtitle: String? = null,
            checked: Boolean = false,
            onChange: ((Boolean) -> Unit)? = null
        ): AppSwitch = AppSwitch(context).apply {
            this.title = title
            this.subtitle = subtitle
            this.isChecked = checked
            this.onCheckedChange = onChange
        }
    }

}
