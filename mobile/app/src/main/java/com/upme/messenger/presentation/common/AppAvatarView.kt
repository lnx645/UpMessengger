package com.upme.messenger.presentation.common

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.upme.messenger.R

private val AVATAR_BG = intArrayOf(
    0xFF2196F3.toInt(), 0xFF9C27B0.toInt(), 0xFF4CAF50.toInt(),
    0xFFFF9800.toInt(), 0xFFE91E63.toInt(), 0xFF009688.toInt(),
    0xFF3F51B5.toInt(), 0xFFFF5722.toInt()
)

/**
 * Reusable avatar lingkaran — initial + seed warna stabil dari id (hash PIN/nama).
 * Dipakai di item_chat (ganti FrameLayout manual) & profil.
 *
 * XML: <com.upme.messenger.presentation.common.AppAvatarView
 *   android:layout_width="49dp" android:layout_height="49dp"
 *   app:avatarTextSize="19sp" android:contentDescription="Avatar teman" />
 */
class AppAvatarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val img: ImageView
    private val txtInitial: TextView
    private var seedKey: String = ""

    var initial: String
        get() = txtInitial.text.toString()
        set(v) { txtInitial.text = v.take(2).uppercase() }

    init {
        var textSizeSp = 19f
        attrs?.let {
            val a = context.obtainStyledAttributes(it, R.styleable.AppAvatarView)
            textSizeSp = a.getDimension(R.styleable.AppAvatarView_avatarTextSize, 19f * resources.displayMetrics.scaledDensity) / resources.displayMetrics.scaledDensity
            a.recycle()
        }
        img = ImageView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = GONE
        }
        txtInitial = TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            gravity = Gravity.CENTER
            textSize = textSizeSp
            typeface = FontCacheShared.bold(context)
            setTextColor(0xFFFFFFFF.toInt())
            background = ResourcesCompat.getDrawable(resources, R.drawable.bg_avatar_circle, context.theme)
        }
        addView(txtInitial)
        addView(img)
        isFocusable = false
    }

    /** Set avatar dari id stabil (PIN) + nama — warna background konsisten per id. */
    fun bind(id: String, name: String) {
        seedKey = id
        val label = name.takeIf { it.isNotBlank() } ?: id
        initial = label.take(1)
        val bg = txtInitial.background?.mutate()
        bg?.setTint(AVATAR_BG[(id.hashCode().and(0x7fffffff)) % AVATAR_BG.size])
        contentDescription = context.getString(R.string.cd_avatar, label)
    }

    fun setImageFile(file: java.io.File, load: (ImageView, java.io.File) -> Unit) {
        if (file.exists()) {
            img.visibility = VISIBLE
            txtInitial.visibility = GONE
            load(img, file)
        } else {
            img.visibility = GONE
            txtInitial.visibility = VISIBLE
        }
    }
}
