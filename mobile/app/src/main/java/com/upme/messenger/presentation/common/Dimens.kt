package com.upme.messenger.presentation.common

import android.content.res.Resources
import android.util.TypedValue

val Int.dp: Int get() = (this * Resources.getSystem().displayMetrics.density).toInt()
val Float.dp: Float get() = this * Resources.getSystem().displayMetrics.density
fun Int.dpF(): Float = this * Resources.getSystem().displayMetrics.density

fun dp(v: Float): Float = v * Resources.getSystem().displayMetrics.density
fun dp(v: Int): Int = (v * Resources.getSystem().displayMetrics.density).toInt()
fun dpToPx(v: Float, res: Resources): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, res.displayMetrics).toInt()
