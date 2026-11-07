package com.upme.messenger.presentation.auth
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import com.upme.messenger.presentation.common.AppToast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.upme.messenger.R
import com.upme.messenger.data.auth.AuthPrefs
import com.upme.messenger.presentation.avatar.UserAvatarCroppingFragment
import com.upme.messenger.presentation.common.ClickGuard
import com.upme.messenger.presentation.common.setLoading
import coil.load
import java.io.File

class SetupProfilFragment : Fragment() {
  private var avatarUri: Uri? = null
  private val pickLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
    if (uri != null) openCrop(uri)
  }

  private fun openCrop(uri: Uri) {
    parentFragmentManager.beginTransaction()
      .replace(R.id.container, UserAvatarCroppingFragment.newInstance(uri))
      .addToBackStack("crop")
      .commit()
  }

  override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View = i.inflate(R.layout.fragment_setup_profil, c, false)
  override fun onViewCreated(v: View, s: Bundle?) {
    val pin = arguments?.getString("pin") ?: AuthPrefs(requireContext()).pin ?: "----"
    val name0 = arguments?.getString("name") ?: ""
    v.findViewById<TextView>(R.id.txtPinPreview).text = "PIN: ${pin.chunked(4).joinToString(" ")}"
    val input = v.findViewById<EditText>(R.id.inputNama)
    input.setText(name0)
    v.findViewById<TextView>(R.id.txtInitial).text = (input.text.toString().take(1).ifEmpty { "U" }).uppercase()
    // load saved avatar if exists
    val avatarFile = File(requireContext().filesDir, "avatar.jpg")
    if (avatarFile.exists()) {
      v.findViewById<ImageView>(R.id.imgAvatar)?.apply { visibility = View.VISIBLE; load(avatarFile) { crossfade(true) } }
      v.findViewById<TextView>(R.id.txtInitial).visibility = View.GONE
    }
    // avatar click -> pick
    v.findViewById<View>(R.id.avatarContainer)?.setOnClickListener {
      if (!ClickGuard.canClick()) return@setOnClickListener
      pickLauncher.launch("image/*")
    }
    // result from cropping fragment
    parentFragmentManager.setFragmentResultListener(UserAvatarCroppingFragment.REQUEST_KEY, viewLifecycleOwner) { _, bundle ->
      val uriStr = bundle.getString(UserAvatarCroppingFragment.KEY_URI) ?: return@setFragmentResultListener
      val uri = Uri.parse(uriStr)
      avatarUri = uri
      val iv = v.findViewById<ImageView>(R.id.imgAvatar)
      iv.visibility = View.VISIBLE
      iv.load(uri) { crossfade(true) }
      v.findViewById<TextView>(R.id.txtInitial).visibility = View.GONE
      AppToast.show(requireContext(), "Avatar siap")
    }
    input.addTextChangedListener(object : android.text.TextWatcher {
      override fun afterTextChanged(s: android.text.Editable?) { v.findViewById<TextView>(R.id.txtInitial).text = (s?.toString()?.take(1)?.uppercase() ?: "U") }
      override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
      override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
    })
    val btn = v.findViewById<MaterialButton>(R.id.btnSelesai)
    val progress = v.findViewById<ProgressBar>(R.id.progressSelesai)
    btn.setOnClickListener {
      if (!ClickGuard.canClick()) return@setOnClickListener
      val nama = input.text.toString().trim()
      if (nama.length < 2) { context?.let { AppToast.show(it, "Nama minimal 2 huruf") }; return@setOnClickListener }
      btn.setLoading(true, progress)
      AuthPrefs(requireContext()).name = nama
      context?.let { AppToast.show(it, "Selamat datang, $nama!") }
      v.postDelayed({
        try {
          val activity = requireActivity()
          val intent = android.content.Intent(activity, com.upme.messenger.presentation.MainActivity::class.java)
          intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
          startActivity(intent)
          activity.finish()
        } catch (_: Exception) {
          try { requireActivity().recreate() } catch (_: Exception) {}
        }
      }, 400)
    }
  }
}
