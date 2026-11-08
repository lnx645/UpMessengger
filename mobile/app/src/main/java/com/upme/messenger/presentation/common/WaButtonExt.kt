package com.upme.messenger.presentation.common

import android.os.SystemClock
import android.view.View
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * WA-style loading + per-view debounce — single source, no global timestamp.
 * U3+U4 fix: satu API loading + satu debounce per-view via view tag (elapsedRealtime).
 */

private const val KEY_LAST_CLICK = -0x7f00_0001 // tag key for per-view debounce

fun View.debounceClick(intervalMs: Long = 500L, action: (View) -> Unit) {
  setOnClickListener { v ->
    val now = SystemClock.elapsedRealtime()
    val last = (v.getTag(KEY_LAST_CLICK) as? Long) ?: 0L
    if (now - last < intervalMs) return@setOnClickListener
    v.setTag(KEY_LAST_CLICK, now)
    action(v)
  }
}

/**
 * Unified loading API — isLoading + optional progress + optional loadingText.
 * Keep text visible with alpha 0.6 WA-style; if loadingText provided, swap text.
 */
fun MaterialButton.setLoading(isLoading: Boolean, progress: View? = null, loadingText: String? = null) {
  if (!isLoading) {
    // restore original text from tag if swapped
    val orig = getTag(KEY_LAST_CLICK + 1) as? String
    if (orig != null) text = orig
  } else if (loadingText != null) {
    setTag(KEY_LAST_CLICK + 1, text.toString())
    text = loadingText
  }
  isEnabled = !isLoading
  isClickable = !isLoading
  alpha = if (isLoading) 0.6f else 1f
  if (progress != null) progress.visibility = if (isLoading) View.VISIBLE else View.GONE
}

fun FloatingActionButton.setLoading(isLoading: Boolean, progress: View? = null) {
  isEnabled = !isLoading
  isClickable = !isLoading
  alpha = if (isLoading) 0.6f else 1f
  if (progress != null) progress.visibility = if (isLoading) View.VISIBLE else View.GONE
}

/** Tiny per-owner debouncer untuk kasus non-View (mis. klik item adapter yang membuka navigasi). */
class PerViewDebounce(private val intervalMs: Long = 500L) {
    private var last = 0L
    @Synchronized
    fun canClick(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - last < intervalMs) return false
        last = now
        return true
    }
}

/** Cek debounce per-view tanpa memasang listener — untuk guard di dalam listener yang sudah ada. */
fun View.debounceCanClick(intervalMs: Long = 500L): Boolean {
    val now = SystemClock.elapsedRealtime()
    val last = (getTag(KEY_LAST_CLICK) as? Long) ?: 0L
    if (now - last < intervalMs) return false
    setTag(KEY_LAST_CLICK, now)
    return true
}

@Deprecated("Use View.debounceCanClick per-view")
object ClickGuard {
    private var lastGlobal = 0L
    fun canClick(intervalMs: Long = 500L): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastGlobal < intervalMs) return false
        lastGlobal = now
        return true
    }
    fun canClick(view: View, intervalMs: Long = 500L): Boolean = view.debounceCanClick(intervalMs)
}

