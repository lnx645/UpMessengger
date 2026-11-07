package com.upme.messenger
import android.app.Application
/** Application minimal agar startup cepat: tidak init berat di sini, semua lazy via Startup. */
class UpMeApp : Application() {
  // ServiceLocator manual (tanpa Hilt) agar build hemat RAM dan APK kecil.
  val locator by lazy { ServiceLocator(this) }
  override fun onCreate() {
    super.onCreate()
    // Sengaja kosong: inisialisasi Room/TCP/Signal dilakukan lazy saat pertama dipakai.
  }
}
