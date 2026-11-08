package com.upme.messenger.presentation.common

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.card.MaterialCardView
import com.upme.messenger.R

/**
 * Reusable settings section — Material3, Manrope, clean.
 *
 * Card putih 16dp yang mengelompokkan beberapa [SettingItem] / [AppSwitch]
 * dengan divider 0.7dp inset 66dp otomatis di antara item.
 *
 * Optional header label di luar card (mis. "PENGATURAN") via [sectionTitle].
 *
 * Usage code:
 *   val section = SettingSection(context).apply {
 *       sectionTitle = "Pengaturan"
 *       addItem(SettingItem(context).apply { title = "Notifikasi"; iconRes = R.drawable.ic_bell })
 *       addItem(SettingItem(context).apply { title = "Tampilan"; iconRes = R.drawable.ic_palette })
 *       addSwitch(AppSwitch(context).apply { title = "Mode Gelap" })
 *   }
 *   parentLinear.addView(section)
 *
 * Divider otomatis — tidak perlu AppDivider manual di antara item.
 */
class SettingSection @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val titleView: TextView
    private val card: MaterialCardView
    private val cardContent: LinearLayout

    var sectionTitle: String?
        get() = titleView.text.toString().takeIf { titleView.visibility == VISIBLE }
        set(value) {
            if (value.isNullOrBlank()) {
                titleView.visibility = GONE
                titleView.text = ""
            } else {
                titleView.text = value
                titleView.visibility = VISIBLE
            }
        }

    init {
        orientation = VERTICAL
        val manrope = ResourcesCompat.getFont(context, R.font.manrope_regular)
        val manropeBold = ResourcesCompat.getFont(context, R.font.manrope_bold)

        titleView = TextView(context).apply {
            textSize = 11f
            setTextColor(0xFF667781.toInt())
            typeface = manropeBold ?: manrope
            letterSpacing = 0.06f
            visibility = GONE
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = resources.dp(8)
                marginStart = resources.dp(4)
            }
        }

        card = MaterialCardView(context).apply {
            radius = resources.dp(16).toFloat()
            cardElevation = 0f
            strokeWidth = 0
            setCardBackgroundColor(0xFFFFFFFF.toInt())
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        cardContent = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        card.addView(cardContent)
        addView(titleView)
        addView(card)

        // padding outer handled by parent; card handles inner via item padding
        val lp = layoutParams
        if (lp is MarginLayoutParams) {
            // ensure bottom spacing between sections when stacked
            lp.bottomMargin = resources.dp(12)
            layoutParams = lp
        } else {
            // if no parent yet, set via default
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = resources.dp(12)
            }
        }
    }

    /** Tambah SettingItem — divider inset otomatis bila bukan item pertama. */
    fun addItem(item: SettingItem) {
        if (cardContent.childCount > 0) cardContent.addView(createDivider())
        cardContent.addView(item)
    }

    /** Tambah AppSwitch row — divider otomatis. */
    fun addSwitch(switchRow: AppSwitch) {
        if (cardContent.childCount > 0) cardContent.addView(createDivider())
        cardContent.addView(switchRow)
    }

    /** Tambah view custom apapun (divider otomatis). */
    fun addRow(view: View) {
        if (cardContent.childCount > 0) cardContent.addView(createDivider())
        cardContent.addView(view)
    }

    /** Hapus semua item dari section (judul tetap). */
    fun clearItems() {
        cardContent.removeAllViews()
    }

    private fun createDivider(): View {
        return View(context).apply {
            setBackgroundColor(0x0F000000)
            val h = (0.7f * resources.displayMetrics.density).toInt().coerceAtLeast(1)
            val inset = (AppDivider.INSET_SETTINGS_DP * resources.displayMetrics.density).toInt()
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, h).apply {
                marginStart = inset
            }
        }
    }

    // dp via Dimens.kt
}
