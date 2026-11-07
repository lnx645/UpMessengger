package com.upme.messenger.presentation
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.upme.messenger.R
import com.upme.messenger.data.auth.AuthPrefs
import com.upme.messenger.presentation.auth.WelcomeFragment
import com.upme.messenger.presentation.chats.ChatsFragment
import com.upme.messenger.presentation.detail.ChatDetailFragment
import com.upme.messenger.presentation.profil.ProfilFragment
import com.upme.messenger.presentation.teman.TemanFragment
import com.upme.messenger.presentation.updates.UpdatesFragment

class MainActivity : AppCompatActivity() {
  private lateinit var toolbar: MaterialToolbar
  private lateinit var bottomNav: BottomNavigationView
  private lateinit var authPrefs: AuthPrefs
  private val titles = mapOf(R.id.nav_chats to "UpMessenger", R.id.nav_updates to "Updates", R.id.nav_teman to "Teman", R.id.nav_profil to "Profil", R.id.nav_detail to "Chat")

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    toolbar = findViewById(R.id.toolbar)
    bottomNav = findViewById(R.id.bottomNav)
    authPrefs = AuthPrefs(this)

    toolbar.setTitleTextColor(getColor(android.R.color.white))
    toolbar.setBackgroundColor(getColor(R.color.upme_green_dark))
    toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

    // WhatsApp arsitektur: cek sesi, belum login -> Welcome, sudah -> Chats
    if (savedInstanceState == null) {
      if (!authPrefs.isLoggedIn) {
        showAuth()
      } else {
        show(ChatsFragment(), R.id.nav_chats, showBottomNav = true)
      }
    } else {
      updateChrome()
    }

    bottomNav.setOnItemSelectedListener { item ->
      when (item.itemId) {
        R.id.nav_chats -> { show(ChatsFragment(), item.itemId, true); true }
        R.id.nav_updates -> { show(UpdatesFragment(), item.itemId, true); true }
        R.id.nav_teman -> { show(TemanFragment(), item.itemId, true); true }
        R.id.nav_profil -> { show(ProfilFragment(), item.itemId, true); true }
        else -> false
      }
    }
    supportFragmentManager.addOnBackStackChangedListener { updateChrome() }
    updateChrome()
  }

  private fun showAuth() {
    toolbar.title = "UpMessenger"
    bottomNav.visibility = View.GONE
    toolbar.navigationIcon = null
    supportFragmentManager.beginTransaction().replace(R.id.container, WelcomeFragment()).commit()
  }

  private fun show(f: Fragment, id: Int, showBottomNav: Boolean) {
    // Guard: jika belum login dan coba akses tab utama, paksa ke auth
    if (!authPrefs.isLoggedIn && showBottomNav) {
      showAuth()
      return
    }
    if (showBottomNav) supportFragmentManager.popBackStack()
    supportFragmentManager.beginTransaction().replace(R.id.container, f, id.toString()).apply {
      if (!showBottomNav) addToBackStack(id.toString())
    }.commit()
    toolbar.title = titles[id]
    updateChromeFor(showBottomNav, id)
  }

  fun openDetail(conversationId: String, name: String) {
    val f = ChatDetailFragment().apply { arguments = Bundle().apply { putString("conv", conversationId); putString("name", name) } }
    supportFragmentManager.beginTransaction().replace(R.id.container, f, "detail").addToBackStack("detail").commit()
    toolbar.title = name
    updateChromeFor(false, R.id.nav_detail)
  }

  private fun updateChrome() {
    val cur = supportFragmentManager.findFragmentById(R.id.container)
    val isCropping = cur is com.upme.messenger.presentation.avatar.UserAvatarCroppingFragment
    if (isCropping) {
      bottomNav.visibility = View.GONE
      toolbar.visibility = View.GONE
      return
    }
    val isDetail = cur is ChatDetailFragment
    val isAuth = cur is WelcomeFragment || cur is com.upme.messenger.presentation.auth.PhoneFragment || cur is com.upme.messenger.presentation.auth.OtpFragment || cur is com.upme.messenger.presentation.auth.SetupProfilFragment
    if (isAuth) {
      bottomNav.visibility = View.GONE
      toolbar.visibility = View.VISIBLE
      toolbar.navigationIcon = null
      toolbar.title = "UpMessenger"
      return
    }
    toolbar.visibility = View.VISIBLE
    updateChromeFor(!isDetail, if (isDetail) R.id.nav_detail else bottomNav.selectedItemId)
  }

  private fun updateChromeFor(showBottomNav: Boolean, selectedId: Int) {
    if (!authPrefs.isLoggedIn) {
      bottomNav.visibility = View.GONE
      return
    }
    bottomNav.visibility = if (showBottomNav) View.VISIBLE else View.GONE
    if (showBottomNav) {
      toolbar.navigationIcon = null
      toolbar.title = titles[selectedId] ?: titles[R.id.nav_chats]
    } else {
      toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
      toolbar.navigationIcon?.setTint(getColor(android.R.color.white))
    }
  }
}
