package com.upme.messenger.presentation.common

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.google.android.material.button.MaterialButton
import com.upme.messenger.R

/**
 * Reusable empty-state — icon + title + subtitle + CTA.
 * Dipakai di fragment_teman & fragment_chats (ganti LinearLayout manual +
 * translationY/margin fixed yang rapuh).
 *
 * XML: <com.upme.messenger.presentation.common.AppEmptyState
 *   android:id="@+id/emptyState" android:layout_width="match_parent"
 *   android:layout_height="wrap_content" android:layout_gravity="center"
 *   app:emptyTitle="@string/empty_teman_title" ... />
 */
class AppEmptyState @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val img: ImageView
    private val txtTitle: TextView
    private val txtSubtitle: TextView
    private val btnCta: MaterialButton

    var iconRes: Int = 0
        set(v) {
            field = v
            if (v != 0) { img.setImageResource(v); img.visibility = View.VISIBLE }
            else img.visibility = View.GONE
        }

    var title: String
        get() = txtTitle.text.toString()
        set(v) { txtTitle.text = v; txtTitle.visibility = if (v.isBlank()) View.GONE else View.VISIBLE }

    var subtitle: String?
        get() = txtSubtitle.text.toString().takeIf { txtSubtitle.visibility == View.VISIBLE }
        set(v) {
            if (v.isNullOrBlank()) txtSubtitle.visibility = View.GONE
            else { txtSubtitle.text = v; txtSubtitle.visibility = View.VISIBLE }
        }

    var ctaText: String?
        get() = btnCta.text.toString().takeIf { btnCta.visibility == View.VISIBLE }
        set(v) {
            if (v.isNullOrBlank()) btnCta.visibility = View.GONE
            else { btnCta.text = v; btnCta.visibility = View.VISIBLE }
        }

    var onCtaClick: (() -> Unit)? = null

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        val pad = context.dp(24)
        setPadding(pad, pad, pad, pad)
        var aTitle: String? = null
        var aSubtitle: String? = null
        var aCta: String? = null
        var aIcon = 0
        attrs?.let {
            val a = context.obtainStyledAttributes(it, R.styleable.AppEmptyState)
            aIcon = a.getResourceId(R.styleable.AppEmptyState_emptyIcon, 0)
            aTitle = a.getString(R.styleable.AppEmptyState_emptyTitle)
            aSubtitle = a.getString(R.styleable.AppEmptyState_emptySubtitle)
            aCta = a.getString(R.styleable.AppEmptyState_emptyCta)
            a.recycle()
        }
        img = ImageView(context).apply {
            layoutParams = LayoutParams(context.dp(80), context.dp(80)).apply {
                bottomMargin = context.dp(16)
            }
            alpha = 0.7f
            visibility = View.GONE
            contentDescription = null
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        txtTitle = TextView(context).apply {
            setTextAppearance(R.style.TextAppearance_UpMe_Title)
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        txtSubtitle = TextView(context).apply {
            setTextAppearance(R.style.TextAppearance_UpMe_Body)
            gravity = Gravity.CENTER
            visibility = View.GONE
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = context.dp(6)
            }
        }
        btnCta = MaterialButton(context).apply {
            backgroundTintList = android.content.res.ColorStateList.valueOf(context.getColor(R.color.upme_green_dark))
            setTextColor(context.getColor(android.R.color.white))
            cornerRadius = context.dp(18)
            visibility = View.GONE
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = context.dp(16)
            }
            setOnClickListener { onCtaClick?.invoke() }
        }
        addView(img)
        addView(txtTitle)
        addView(txtSubtitle)
        addView(btnCta)
        if (aIcon != 0) iconRes = aIcon
        if (aTitle != null) title = aTitle!!
        if (aSubtitle != null) subtitle = aSubtitle
        if (aCta != null) ctaText = aCta
    }

    /** CDN-free: pakai drawable lokal agar offline-safe. */
    fun setIcon(@DrawableRes res: Int) { iconRes = res }
}
