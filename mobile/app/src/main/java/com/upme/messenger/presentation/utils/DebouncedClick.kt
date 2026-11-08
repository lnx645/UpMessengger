package com.upme.messenger.presentation.utils

import android.view.View

/** @deprecated Pakai View.debounceClick di presentation.common (per-view, elapsedRealtime) */
@Deprecated("Use View.debounceClick")
fun View.setDebouncedClickListener(debounceMs: Long = 800L, onClick: (View) -> Unit) {
  this.setOnClickListener { v -> onClick(v) }
}

/** @deprecated Legacy overload — no-op, use WaButtonExt.setLoading(isLoading, progress) */
fun com.google.android.material.button.MaterialButton.setLoadingLegacy(
  progress: android.widget.ProgressBar,
  loading: Boolean,
  originalText: String
) {
}
