package com.upme.messenger
import android.content.Context
import com.upme.messenger.data.local.UpMeDb
import com.upme.messenger.data.net.TcpClient
/** DI manual production: hemat build RAM (tanpa KSP/Hilt), singleton lazy dan testable. */
class ServiceLocator(ctx: Context) {
  private val appCtx = ctx.applicationContext
  // DB Room + SQLCipher dibuat sekali, lazy agar startup < 800ms.
  val db: UpMeDb by lazy { UpMeDb.create(appCtx) }
  // Satu koneksi TCP persisten per proses, reconnect otomatis di dalam TcpClient.
  val tcp: TcpClient by lazy { TcpClient(db.messageDao()) }
}
