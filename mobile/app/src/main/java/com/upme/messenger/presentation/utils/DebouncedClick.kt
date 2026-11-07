package com.upme.messenger.presentation.utils

import android.view.View

/** Cegah double-click WA-style: ignore click dalam 800ms */
fun View.setDebouncedClickListener(debounceMs: Long = 800L, onClick: (View) -> Unit) {
  var lastClick = 0L
  setOnClickListener { v ->
    val now = System.currentTimeMillis()
    if (now - lastClick < debounceMs) return@setOnClickListener
    lastClick = now
    onClick(v)
  }
}

/** Helper loading untuk MaterialButton + ProgressBar */
fun com.google.android.material.button.MaterialButton.setLoading(
  progress: android.widget.ProgressBar,
  loading: Boolean,
  originalText: String
) {
  isEnabled = !loading
  progress.visibility = if (loading) View.VISIBLE else View.GONE
  text = if (loading) "" else originalText
  alpha = if (loading) 0.85f else 1f
}
