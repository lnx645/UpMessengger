package com.upme.messenger.presentation.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.upme.messenger.R

/**
 * Reusable WA-style custom Toast — profesional, bukan android.widget.Toast bawaan.
 * Menggunakan overlay pada decorView, font Manrope, background gelap rounded.
 * Semua pemanggilan Toast.makeText harus diganti ke AppToast.
 */
object AppToast {
    private var currentView: android.view.View? = null
    private val handler = Handler(Looper.getMainLooper())
    private val dismissRunnable = Runnable { dismiss() }

    private fun getActivity(context: Context): Activity? {
        var c: Context? = context
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }

    private fun dismiss() {
        currentView?.let { v ->
            try {
                val wm = v.getTag(R.id.txtToast) as? android.view.WindowManager
                if (wm != null) {
                    try { wm.removeView(v) } catch (_: Exception) {}
                } else {
                    (v.parent as? ViewGroup)?.removeView(v)
                }
            } catch (_: Exception) {
                try { (v.parent as? ViewGroup)?.removeView(v) } catch (_: Exception) {}
            }
        }
        currentView = null
        handler.removeCallbacks(dismissRunnable)
    }

    @JvmStatic
    fun show(context: Context, message: String, durationMs: Long = 2200L) {
        if (message.isBlank()) return
        val activity = getActivity(context) ?: return
        dismiss()
        try {
            val inflater = LayoutInflater.from(activity)
            val view = inflater.inflate(R.layout.toast_custom, null)
            view.findViewById<TextView>(R.id.txtToast).text = message
            view.alpha = 0f
            // Gunakan WindowManager agar di atas semua sheet/dialog (z-index tertinggi)
            val wm = activity.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
            val params = android.view.WindowManager.LayoutParams(
                android.view.WindowManager.LayoutParams.WRAP_CONTENT,
                android.view.WindowManager.LayoutParams.WRAP_CONTENT,
                android.view.WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = (72 * activity.resources.displayMetrics.density).toInt()
                windowAnimations = android.R.style.Animation_Toast
            }
            // simpan wm dan view untuk dismiss
            wm.addView(view, params)
            currentView = view
            // simpan wm di tag untuk remove
            view.setTag(R.id.txtToast, wm)
            view.animate().alpha(1f).setDuration(180).start()
            handler.postDelayed(dismissRunnable, durationMs)
            handler.postDelayed({
                currentView?.animate()?.alpha(0f)?.setDuration(180)?.withEndAction { dismiss() }?.start()
            }, durationMs - 200)
        } catch (_: Exception) {
            // fallback ke decor jika WindowManager gagal (permission)
            try {
                val inflater = LayoutInflater.from(activity)
                val view = inflater.inflate(R.layout.toast_custom, null)
                view.findViewById<TextView>(R.id.txtToast).text = message
                val decor = activity.window.decorView as ViewGroup
                val params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    bottomMargin = (56 * activity.resources.displayMetrics.density).toInt()
                }
                val parent: ViewGroup = decor.findViewById(android.R.id.content) ?: decor
                parent.addView(view, params)
                currentView = view
            } catch (_: Exception) {}
        }
    }

    @JvmStatic
    fun showShort(context: Context, message: String) = show(context, message, 2000L)

    @JvmStatic
    fun showLong(context: Context, message: String) = show(context, message, 3200L)

    // helpers semantik (dipakai bila perlu beda warna — saat ini sama, tapi siap extend)
    @JvmStatic
    fun success(context: Context, message: String) = show(context, message, 2200L)

    @JvmStatic
    fun error(context: Context, message: String) = show(context, message, 2600L)
}
