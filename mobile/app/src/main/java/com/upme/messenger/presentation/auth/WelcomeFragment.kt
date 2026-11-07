package com.upme.messenger.presentation.auth
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import com.upme.messenger.R

class WelcomeFragment : Fragment() {
  override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View = i.inflate(R.layout.fragment_welcome, c, false)
  override fun onViewCreated(v: View, s: Bundle?) {
    v.findViewById<View>(R.id.btnMulai).setOnClickListener {
      parentFragmentManager.beginTransaction().replace(R.id.container, PhoneFragment()).addToBackStack(null).commit()
    }
  }
}
