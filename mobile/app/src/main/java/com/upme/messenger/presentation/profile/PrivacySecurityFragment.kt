package com.upme.messenger.presentation.profile

import android.os.Bundle
import android.view.*
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.upme.messenger.R
import com.upme.messenger.data.auth.AuthPrefs
import com.upme.messenger.data.auth.PrivacyPrefs
import com.upme.messenger.presentation.common.AppSwitch
import com.upme.messenger.presentation.common.AppToast
import com.upme.messenger.presentation.common.SettingSection
import kotlinx.coroutines.launch

/**
 * Fragment Privasi & Keamanan — pertemanan auto-approve.
 *
 * 3 toggle pakai common UI (SettingSection + AppSwitch):
 * 1. Siapa bisa tambah saya — semua orang / terbatas
 * 2. Auto-approve pertemanan — langsung jadi teman tanpa konfirmasi
 * 3. Blok invite baru — tolak semua invite masuk
 *
 * Simpan di EncryptedSharedPreferences (PrivacyPrefs) + sync DETS field autoApprove via TCP 0x30.
 */
class PrivacySecurityFragment : Fragment() {

    private lateinit var privacyPrefs: PrivacyPrefs
    private lateinit var authPrefs: AuthPrefs

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val ctx = requireContext()
        privacyPrefs = PrivacyPrefs(ctx)
        authPrefs = AuthPrefs(ctx)

        val scroll = ScrollView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(0xFFF0F2F5.toInt())
            isFillViewport = true
        }

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            val pad = (12 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }

        val manrope = ResourcesCompat.getFont(ctx, R.font.manrope_regular)

        // Header desc
        val header = TextView(ctx).apply {
            text = "Atur siapa yang bisa menambahkanmu sebagai teman dan bagaimana invite diproses."
            textSize = 12f
            setTextColor(0xFF667781.toInt())
            typeface = manrope
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (12 * resources.displayMetrics.density).toInt()
                marginStart = (4 * resources.displayMetrics.density).toInt()
            }
        }
        root.addView(header)

        // Section: Pertemanan
        val sectionPertemanan = SettingSection(ctx).apply {
            sectionTitle = "PERTEMANAN"
        }

        val switchWhoCanAdd = AppSwitch(ctx).apply {
            title = "Siapa bisa tambah saya"
            subtitle = if (privacyPrefs.whoCanAdd) "Semua orang bisa invite" else "Hanya kontak yang bisa invite"
            isChecked = privacyPrefs.whoCanAdd
        }

        val switchAutoApprove = AppSwitch(ctx).apply {
            title = "Auto-approve pertemanan"
            subtitle = if (privacyPrefs.autoApprove) "Invite langsung jadi teman" else "Perlu konfirmasi manual"
            isChecked = privacyPrefs.autoApprove
        }

        val switchBlockInvite = AppSwitch(ctx).apply {
            title = "Blok invite baru"
            subtitle = if (privacyPrefs.blockInvite) "Semua invite otomatis ditolak" else "Invite masuk akan ditampilkan"
            isChecked = privacyPrefs.blockInvite
        }

        // Listeners: simpan ke EncryptedSharedPreferences + sync DETS field autoApprove
        switchWhoCanAdd.onCheckedChange = { checked ->
            privacyPrefs.whoCanAdd = checked
            switchWhoCanAdd.subtitle = if (checked) "Semua orang bisa invite" else "Hanya kontak yang bisa invite"
            context?.let { AppToast.show(it, if (checked) "Siapa bisa tambah: Semua orang" else "Siapa bisa tambah: Terbatas") }
            syncToServer()
            // Mutual exclusion: jika blok invite aktif, whoCanAdd dimatikan visual hint
            if (checked && privacyPrefs.blockInvite) {
                // tetap izinkan, tapi info
            }
        }

        switchAutoApprove.onCheckedChange = { checked ->
            privacyPrefs.autoApprove = checked
            switchAutoApprove.subtitle = if (checked) "Invite langsung jadi teman" else "Perlu konfirmasi manual"
            context?.let { AppToast.show(it, if (checked) "Auto-approve aktif" else "Auto-approve nonaktif") }
            syncToServer()
        }

        switchBlockInvite.onCheckedChange = { checked ->
            privacyPrefs.blockInvite = checked
            switchBlockInvite.subtitle = if (checked) "Semua invite otomatis ditolak" else "Invite masuk akan ditampilkan"
            context?.let { AppToast.show(it, if (checked) "Blok invite aktif" else "Blok invite nonaktif") }
            // Jika blok aktif, auto-approve tidak relevan — tetap simpan tapi beri hint
            if (checked) {
                // optional: nonaktifkan autoApprove visual? tetap biarkan user atur
            }
            syncToServer()
        }

        sectionPertemanan.addSwitch(switchWhoCanAdd)
        sectionPertemanan.addSwitch(switchAutoApprove)
        sectionPertemanan.addSwitch(switchBlockInvite)

        root.addView(sectionPertemanan)

        // Info footer
        val footer = TextView(ctx).apply {
            text = "Perubahan disimpan aman di perangkat (EncryptedSharedPreferences) dan disinkronkan ke server (DETS autoApprove)."
            textSize = 11f
            setTextColor(0xFF8696A0.toInt())
            typeface = manrope
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (8 * resources.displayMetrics.density).toInt()
                marginStart = (4 * resources.displayMetrics.density).toInt()
            }
        }
        root.addView(footer)

        scroll.addView(root)
        return scroll
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        super.onViewCreated(v, s)
        // Pull dari server untuk sinkronisasi awal (jika login)
        val phone = authPrefs.phone
        val token = authPrefs.token
        if (phone != null && token != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val result = privacyPrefs.pullFromServer(phone, token)
                    if (result.isSuccess) {
                        // Update UI setelah pull
                        refreshSwitches(v)
                    }
                } catch (_: Exception) { }
            }
        }
    }

    private fun syncToServer() {
        val phone = authPrefs.phone ?: return
        val token = authPrefs.token ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val r = privacyPrefs.syncAutoApproveToServer(phone, token)
                r.onFailure { e ->
                    context?.let { AppToast.show(it, "Sync gagal: ${e.message}") }
                }
            } catch (_: Exception) { }
        }
    }

    private fun refreshSwitches(root: View) {
        // Traverse to find AppSwitch instances and update checked state tanpa trigger sync
        fun traverse(view: View) {
            if (view is AppSwitch) {
                when (view.title) {
                    "Siapa bisa tambah saya" -> {
                        view.setCheckedSilent(privacyPrefs.whoCanAdd)
                        view.subtitle = if (privacyPrefs.whoCanAdd) "Semua orang bisa invite" else "Hanya kontak yang bisa invite"
                    }
                    "Auto-approve pertemanan" -> {
                        view.setCheckedSilent(privacyPrefs.autoApprove)
                        view.subtitle = if (privacyPrefs.autoApprove) "Invite langsung jadi teman" else "Perlu konfirmasi manual"
                    }
                    "Blok invite baru" -> {
                        view.setCheckedSilent(privacyPrefs.blockInvite)
                        view.subtitle = if (privacyPrefs.blockInvite) "Semua invite otomatis ditolak" else "Invite masuk akan ditampilkan"
                    }
                }
            } else if (view is ViewGroup) {
                for (i in 0 until view.childCount) traverse(view.getChildAt(i))
            }
        }
        traverse(root)
    }

}
