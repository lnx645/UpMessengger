package com.upme.messenger.presentation.common

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.upme.messenger.R

/**
 * Reusable avatar lingkaran — image + fallback initial.
 * U8: ganti FrameLayout 96dp manual di fragment_profil.
 */
class AppAvatarView @JvmOverloads constructor(
  context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

  private val img: ImageView
  private val txtInitial: TextView

  var initial: String
    get() = txtInitial.text.toString()
    set(v) { txtInitial.text = v.take(2).uppercase() }

  init {
    val manropeBold = ResourcesCompat.getFont(context, R.font.manrope_bold)
    img = ImageView(context).apply {
      layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
      scaleType = ImageView.ScaleType.CENTER_CROP
      visibility = GONE
    }
    txtInitial = TextView(context).apply {
      layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
      gravity = Gravity.CENTER
      textSize = 28f
      typeface = manropeBold
      setTextColor(0xFF9C27B0.toInt())
      background = ResourcesCompat.getDrawable(resources, R.drawable.bg_avatar_parrot, context.theme)
    }
    addView(txtInitial); addView(img)
  }

  fun setImageFile(file: java.io.File, load: (ImageView, java.io.File) -> Unit) {
    if (file.exists()) {
      img.visibility = VISIBLE; txtInitial.visibility = GONE
      load(img, file)
    } else {
      img.visibility = GONE; txtInitial.visibility = VISIBLE
    }
  }

  fun setInitialText(name: String) {
    initial = name
  }
}
