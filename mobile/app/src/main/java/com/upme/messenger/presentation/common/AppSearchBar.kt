package com.upme.messenger.presentation.common

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import com.upme.messenger.R

/**
 * Reusable search bar — icon + EditText + tombol clear (X) + IME search.
 * Ganti search manual di fragment_teman & fragment_chats.
 *
 * XML: <com.upme.messenger.presentation.common.AppSearchBar
 *   android:id="@+id/searchBar" android:layout_width="match_parent"
 *   android:layout_height="wrap_content" app:searchHint="Cari teman" />
 */
class AppSearchBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    val input: EditText
    private val btnClear: ImageView
    private var watcher: TextWatcher? = null

    var hint: String
        get() = input.hint.toString()
        set(v) { input.hint = v }

    var text: String
        get() = input.text.toString()
        set(v) { input.setText(v) }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = context.dp(42)
        setPadding(context.dp(14), 0, context.dp(8), 0)
        background = androidx.core.content.res.ResourcesCompat.getDrawable(
            resources, R.drawable.bg_search_bar, context.theme
        )
        var hintRes: String? = null
        attrs?.let {
            val a = context.obtainStyledAttributes(it, R.styleable.AppSearchBar)
            hintRes = a.getString(R.styleable.AppSearchBar_searchHint)
            a.recycle()
        }
        val icon = ImageView(context).apply {
            layoutParams = LayoutParams(context.dp(18), context.dp(18))
            setImageResource(R.drawable.ic_search)
            contentDescription = null // decorative; EditText hint carries the label
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        addView(icon)
        input = EditText(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = context.dp(10)
            }
            background = null
            textSize = 14f
            isSingleLine = true
            maxLines = 1
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            hint = hintRes ?: context.getString(R.string.hint_search)
            typeface = FontCacheShared.regular(context)
            setHintTextColor(context.getColor(R.color.text_secondary))
            setTextColor(context.getColor(R.color.text_primary))
        }
        addView(input)
        btnClear = ImageView(context).apply {
            layoutParams = LayoutParams(context.dp(32), context.dp(32))
            setImageResource(R.drawable.ic_close)
            contentDescription = context.getString(R.string.cd_clear_search)
            visibility = View.GONE
            setPadding(context.dp(7), context.dp(7), context.dp(7), context.dp(7))
            setOnClickListener { input.text.clear() }
        }
        addView(btnClear)
    }

    fun doOnTextChanged(action: (String) -> Unit) {
        watcher?.let { input.removeTextChangedListener(it) }
        watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                btnClear.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                action(s?.toString() ?: "")
            }
        }
        input.addTextChangedListener(watcher)
    }

    fun setOnSearchAction(action: (String) -> Unit) {
        input.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                action(v.text.toString())
                true
            } else false
        }
    }

    fun clearWatcher() {
        watcher?.let { input.removeTextChangedListener(it) }
        watcher = null
    }
}
