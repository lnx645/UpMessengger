package com.upme.messenger.presentation.common

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.upme.messenger.R

/** Cache typeface app-wide — jangan getFont per instance (jank). */
object FontCacheShared {
    @Volatile private var regular: Typeface? = null
    @Volatile private var medium: Typeface? = null
    @Volatile private var bold: Typeface? = null

    fun regular(c: Context): Typeface? {
        if (regular == null) synchronized(this) {
            if (regular == null) regular = runCatching { ResourcesCompat.getFont(c, R.font.manrope_regular) }.getOrNull()
        }
        return regular
    }
    fun medium(c: Context): Typeface? {
        if (medium == null) synchronized(this) {
            if (medium == null) medium = runCatching { ResourcesCompat.getFont(c, R.font.manrope_medium) }.getOrNull()
        }
        return medium
    }
    fun bold(c: Context): Typeface? {
        if (bold == null) synchronized(this) {
            if (bold == null) bold = runCatching { ResourcesCompat.getFont(c, R.font.manrope_bold) }.getOrNull()
        }
        return bold
    }
}
