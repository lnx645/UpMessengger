package com.upme.messenger.presentation.common

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.upme.messenger.R

/**
 * Global reusable Bottom Sheet — WA-style, Manrope, rounded top, handle.
 * Dipakai untuk: Kirim ulang OTP, konfirmasi umum, pilihan aksi.
 * Semua sheet di app harus lewat sini biar konsisten.
 */
object AppSheet {

    data class Config(
        val title: String,
        val message: String,
        val primaryText: String = "Oke",
        val secondaryText: String? = "Batal",
        val countdownText: String? = null,
        val iconRes: Int? = null,
        val onPrimary: (() -> Unit)? = null,
        val onSecondary: (() -> Unit)? = null,
        val dismissOnPrimary: Boolean = true
    )

    fun show(context: Context, config: Config): BottomSheetDialog {
        val dialog = BottomSheetDialog(context, R.style.ThemeOverlay_App_BottomSheet)
        val view = LayoutInflater.from(context).inflate(R.layout.layout_sheet_global, null)
        val txtTitle = view.findViewById<TextView>(R.id.txtSheetTitle)
        val txtMsg = view.findViewById<TextView>(R.id.txtSheetMessage)
        val txtCountdown = view.findViewById<TextView>(R.id.txtSheetCountdown)
        val btnPrimary = view.findViewById<MaterialButton>(R.id.btnSheetPrimary)
        val btnSecondary = view.findViewById<MaterialButton>(R.id.btnSheetSecondary)
        val imgIcon = view.findViewById<ImageView>(R.id.imgSheetIcon)

        txtTitle.text = config.title
        txtMsg.text = config.message
        btnPrimary.text = config.primaryText

        if (config.countdownText != null) {
            txtCountdown.text = config.countdownText
            txtCountdown.visibility = View.VISIBLE
        } else {
            txtCountdown.visibility = View.GONE
        }

        if (config.iconRes != null) {
            imgIcon.setImageResource(config.iconRes)
            imgIcon.visibility = View.VISIBLE
        }

        if (config.secondaryText != null) {
            btnSecondary.text = config.secondaryText
            btnSecondary.visibility = View.VISIBLE
            btnSecondary.setOnClickListener {
                if (!ClickGuard.canClick(400)) return@setOnClickListener
                config.onSecondary?.invoke()
                dialog.dismiss()
            }
        } else {
            btnSecondary.visibility = View.GONE
        }

        btnPrimary.setOnClickListener {
            if (!ClickGuard.canClick(400)) return@setOnClickListener
            config.onPrimary?.invoke()
            if (config.dismissOnPrimary) dialog.dismiss()
        }

        dialog.setContentView(view)
        // Make sheet rounded corners visible (remove default dim)
        dialog.behavior.isDraggable = true
        dialog.behavior.isHideable = true
        dialog.show()
        return dialog
    }

    /** Helper WA-style untuk Kirim ulang OTP */
    fun showResendOtp(
        context: Context,
        phone: String,
        remainingMs: Long,
        isCooling: Boolean,
        onConfirm: () -> Unit
    ): BottomSheetDialog {
        val cooldownSec = (remainingMs / 1000).toInt().coerceAtLeast(0)
        val countdown = if (isCooling) "Tunggu ${cooldownSec}s sebelum kirim ulang" else null
        return show(
            context, Config(
                title = "Kirim ulang OTP?",
                message = "Kode verifikasi 6 digit akan dikirim via SMS ke $phone.\n\nPastikan nomor aktif dan ada pulsa/sinyal.",
                primaryText = if (isCooling) "Tunggu ${cooldownSec}s" else "Kirim ulang",
                secondaryText = "Batal",
                countdownText = countdown,
                onPrimary = { if (!isCooling) onConfirm() },
                dismissOnPrimary = isCooling
            )
        ).apply {
            // disable primary if cooling
            if (isCooling) {
                val btn = findViewById<MaterialButton>(R.id.btnSheetPrimary)
                btn?.isEnabled = false
                btn?.alpha = 0.5f
            }
        }
    }
}
