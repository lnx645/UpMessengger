package com.upme.messenger.presentation.common

import android.view.View
import android.widget.ProgressBar
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * WA-style loading: disable, alpha 0.6, show progress, prevent double-click via debounce.
 */
fun MaterialButton.setLoading(isLoading: Boolean, progress: View? = null) {
    isEnabled = !isLoading
    isClickable = !isLoading
    alpha = if (isLoading) 0.6f else 1f
    if (progress != null) progress.visibility = if (isLoading) View.VISIBLE else View.GONE
    // sembunyikan teks saat loading jika progress overlay di tengah (opsional: keep text tapi alpha)
    // keep text visible with alpha, WA keeps button shape
}

fun FloatingActionButton.setLoading(isLoading: Boolean, progress: View? = null) {
    isEnabled = !isLoading
    isClickable = !isLoading
    alpha = if (isLoading) 0.6f else 1f
    if (progress != null) progress.visibility = if (isLoading) View.VISIBLE else View.GONE
}

private var lastClickMs = 0L
fun View.debounceClick(intervalMs: Long = 500L, action: (View) -> Unit) {
    setOnClickListener { v ->
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastClickMs < intervalMs) return@setOnClickListener
        lastClickMs = now
        action(v)
    }
}

// guard double-click imperatif (dipakai di coroutine launch)
object ClickGuard {
    private var last = 0L
    fun canClick(intervalMs: Long = 500L): Boolean {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - last < intervalMs) return false
        last = now
        return true
    }
}
fun View.setLoadingSimple(isLoading: Boolean, progress: View? = null) {
    isEnabled = !isLoading
    isClickable = !isLoading
    alpha = if (isLoading) 0.6f else 1f
    if (progress != null) progress.visibility = if (isLoading) View.VISIBLE else View.GONE
}
