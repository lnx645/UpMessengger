package com.upme.messenger.presentation.common

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.content.res.ResourcesCompat
import com.upme.messenger.R

/**
 * Reusable search bar — EditText rounded 10dp + icon.
 * U8: ganti EditText manual di fragment_teman.
 */
class AppSearchBar @JvmOverloads constructor(
  context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

  val input: EditText

  var hint: String
    get() = input.hint.toString()
    set(v) { input.hint = v }

  var text: String
    get() = input.text.toString()
    set(v) { input.setText(v) }

  private var watcher: TextWatcher? = null

  init {
    orientation = HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    setPadding(dp(12), dp(8), dp(12), dp(8))
    background = ResourcesCompat.getDrawable(resources, R.drawable.bg_search_bar, context.theme)
    input = EditText(context).apply {
      layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
      background = null
      textSize = 14f
      isSingleLine = true
      hint = "Cari teman..."
      typeface = ResourcesCompat.getFont(context, R.font.manrope_regular)
      maxLines = 1
    }
    addView(input)
  }

  fun doOnTextChanged(action: (String) -> Unit) {
    watcher?.let { input.removeTextChangedListener(it) }
    watcher = object : TextWatcher {
      override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
      override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
      override fun afterTextChanged(s: Editable?) { action(s?.toString() ?: "") }
    }
    input.addTextChangedListener(watcher)
  }

  fun clearWatcher() {
    watcher?.let { input.removeTextChangedListener(it) }; watcher = null
  }
}
