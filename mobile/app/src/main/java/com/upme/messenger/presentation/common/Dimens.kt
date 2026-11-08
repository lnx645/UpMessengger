package com.upme.messenger.presentation.common

import android.content.Context
import android.content.res.Resources
import android.util.TypedValue

/** Single canonical dp API — selalu pakai context/resources agar ikut density & font-scale config. */
fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
fun Context.dpF(v: Float): Float = v * resources.displayMetrics.density
fun Resources.dp(v: Int): Int = (v * displayMetrics.density).toInt()
fun Resources.dpF(v: Float): Float = v * displayMetrics.density
fun dpPx(v: Float, res: Resources): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, res.displayMetrics).toInt()
