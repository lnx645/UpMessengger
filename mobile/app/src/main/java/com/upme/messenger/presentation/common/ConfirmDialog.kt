package com.upme.messenger.presentation.common

import android.app.Dialog
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.upme.messenger.R

/**
 * Reusable WA-style confirmation dialog — profesional, bukan bawaan AlertDialog.
 * Dipakai untuk: ganti nomor, logout, hapus chat, dll.
 */
class ConfirmDialog(
    private val context: Context,
    private val title: String,
    private val message: String,
    private val positiveText: String = "Ya",
    private val negativeText: String = "Batal",
    private val positiveColor: Int = 0xFF075E54.toInt(),
    private val onPositive: () -> Unit,
    private val onNegative: (() -> Unit)? = null
) {
    private var dialog: Dialog? = null

    fun show() {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_confirm, null)
        view.findViewById<TextView>(R.id.txtConfirmTitle).text = title
        view.findViewById<TextView>(R.id.txtConfirmMessage).text = message
        val btnPos = view.findViewById<MaterialButton>(R.id.btnConfirmPositive)
        val btnNeg = view.findViewById<MaterialButton>(R.id.btnConfirmNegative)
        btnPos.text = positiveText
        btnNeg.text = negativeText
        btnPos.backgroundTintList = android.content.res.ColorStateList.valueOf(positiveColor)

        dialog = Dialog(context).apply {
            setContentView(view)
            setCancelable(true)
            setCanceledOnTouchOutside(true)
            window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        btnPos.debounceClick(400L) {
            dialog?.dismiss()
            onPositive()
        }
        btnNeg.debounceClick(400L) {
            dialog?.dismiss()
            onNegative?.invoke()
        }
        dialog?.show()
    }

    fun dismiss() { dialog?.dismiss() }

    companion object {
        /** Helper one-liner untuk ganti nomor - paling sering dipakai */
        fun showGantiNomor(context: Context, phone: String, onConfirm: () -> Unit) {
            ConfirmDialog(
                context = context,
                title = "Ganti nomor?",
                message = "OTP untuk $phone akan diabaikan dan tidak berlaku lagi. Yakin ingin ganti nomor HP?",
                positiveText = "Ganti",
                negativeText = "Batal",
                onPositive = onConfirm
            ).show()
        }
    }
}
