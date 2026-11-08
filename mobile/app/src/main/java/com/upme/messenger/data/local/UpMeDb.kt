package com.upme.messenger.data.local
import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import java.io.File
import java.security.SecureRandom
/** Entity lokal: sumber kebenaran (local-first). Ciphertext opaque, bukan plaintext server. */
@Entity(tableName = "messages", indices = [Index(value = ["conversationId", "createdAt"])])
data class MessageEntity(@PrimaryKey val id: String, val conversationId: String, val cipher: ByteArray, val status: String, val createdAt: Long)

@Entity(tableName = "teman")
data class TemanEntity(@PrimaryKey val pin: String, val name: String, val addedAt: Long, val status: String = "ACCEPTED")

@Entity(tableName = "pending_requests")
data class PendingEntity(@PrimaryKey val pin: String, val name: String, val phone: String, val createdAt: Long, val direction: String = "IN")

@Dao interface MessageDao {
  @Query("SELECT * FROM messages WHERE conversationId=:conv ORDER BY createdAt DESC LIMIT 50") suspend fun latest(conv: String): List<MessageEntity>
  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(e: MessageEntity)
  @Query("SELECT * FROM messages ORDER BY createdAt DESC LIMIT 50") suspend fun latestAll(): List<MessageEntity>
}

@Dao interface TemanDao {
  @Query("SELECT * FROM teman ORDER BY addedAt DESC") suspend fun all(): List<TemanEntity>
  @Query("SELECT * FROM teman WHERE pin=:pin LIMIT 1") suspend fun find(pin: String): TemanEntity?
  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(e: TemanEntity)
  @Query("DELETE FROM teman WHERE pin=:pin") suspend fun delete(pin: String)
  @Query("SELECT * FROM teman WHERE pin LIKE '%' || :q || '%' OR name LIKE '%' || :q || '%' ORDER BY addedAt DESC LIMIT 50") suspend fun search(q: String): List<TemanEntity>
  @Query("SELECT * FROM teman ORDER BY addedAt DESC LIMIT :limit OFFSET :offset") suspend fun page(limit: Int, offset: Int): List<TemanEntity>
}

@Dao interface PendingDao {
  @Query("SELECT * FROM pending_requests ORDER BY createdAt DESC") suspend fun all(): List<PendingEntity>
  @Query("SELECT * FROM pending_requests WHERE pin=:pin LIMIT 1") suspend fun find(pin: String): PendingEntity?
  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(e: PendingEntity)
  @Query("DELETE FROM pending_requests WHERE pin=:pin") suspend fun delete(pin: String)
  @Query("DELETE FROM pending_requests") suspend fun clear()
}

val MIGRATION_3_4: Migration = object : Migration(3, 4) {
  // Skema sama — bump versi menandai peralihan ke SQLCipher (lihat create()).
  override fun migrate(db: SupportSQLiteDatabase) {}
}

/** Kunci DB 256-bit: EncryptedSharedPreferences, fallback file privat (terbaik yang bisa dijamin tersedia). */
private object DbPassphrase {
  private const val PREFS = "upme_db_key"
  private const val KEY = "db_key_b64"
  fun get(ctx: Context): ByteArray {
    val prefs = runCatching {
      androidx.security.crypto.EncryptedSharedPreferences.create(
        ctx, PREFS, androidx.security.crypto.MasterKey.Builder(ctx).setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM).build(),
        androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }.getOrNull() ?: ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val saved = runCatching { prefs.getString(KEY, null) }.getOrNull()
    if (!saved.isNullOrBlank()) {
      return runCatching { android.util.Base64.decode(saved, android.util.Base64.NO_WRAP) }.getOrNull()
        ?.takeIf { it.size == 32 } ?: newKey(prefs)
    }
    return newKey(prefs)
  }
  private fun newKey(prefs: android.content.SharedPreferences): ByteArray {
    val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
    runCatching { prefs.edit().putString(KEY, android.util.Base64.encodeToString(raw, android.util.Base64.NO_WRAP)).apply() }
    return raw
  }
}

/** Migrasi sekali jalan plaintext -> SQLCipher via sqlcipher_export.
 * Status dicatat di prefs ("db_encrypted") agar tidak perlu probing kunci:
 * probing openDatabase(byte[]) tidak tersedia di SQLCipher 4.5.4 (hanya char[]/String)
 * dan derivasi kunci bisa beda dari SupportFactory(byte[]).
 * Gagal = biarkan plaintext (app tetap jalan, coba lagi next launch). */
private const val ENC_FLAG_PREFS = "upme_db_state"
private const val ENC_FLAG_KEY = "db_encrypted"

private fun isEncryptedFlag(ctx: Context): Boolean =
    runCatching {
        ctx.getSharedPreferences(ENC_FLAG_PREFS, Context.MODE_PRIVATE).getBoolean(ENC_FLAG_KEY, false)
    }.getOrNull() == true

private fun setEncryptedFlag(ctx: Context) {
    runCatching {
        ctx.getSharedPreferences(ENC_FLAG_PREFS, Context.MODE_PRIVATE).edit().putBoolean(ENC_FLAG_KEY, true).apply()
    }
}

private fun migratePlaintextIfNeeded(ctx: Context, passphrase: ByteArray) {
  if (isEncryptedFlag(ctx)) return
  val dbFile = ctx.getDatabasePath("upme.db")
  if (!dbFile.exists() || dbFile.length() == 0L) {
    // Fresh install — langsung terenkripsi, tandai.
    setEncryptedFlag(ctx)
    return
  }
  SQLiteDatabase.loadLibs(ctx)
  // Pastikan ini plaintext (bukan db terenkripsi dari instalasi lama yang flag-nya hilang).
  try {
    SQLiteDatabase.openDatabase(dbFile.absolutePath, "", null, SQLiteDatabase.OPEN_READONLY).close()
  } catch (_: Exception) {
    // Tidak bisa dibuka sebagai plaintext — anggap sudah terenkripsi, tandai agar tak diulang.
    setEncryptedFlag(ctx)
    return
  }
  val tmp = File(ctx.cacheDir, "upme_enc_tmp.db")
  runCatching { tmp.delete() }
  try {
    val hexKey = passphrase.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    val plain = SQLiteDatabase.openDatabase(dbFile.absolutePath, "", null, SQLiteDatabase.OPEN_READWRITE)
    val userVersion = plain.version
    plain.rawExecSQL("ATTACH DATABASE '" + tmp.absolutePath + "' AS encrypted KEY \"x'" + hexKey + "'\";")
    plain.rawExecSQL("SELECT sqlcipher_export('encrypted');")
    plain.rawExecSQL("PRAGMA encrypted.user_version = " + userVersion + ";")
    plain.rawExecSQL("DETACH DATABASE encrypted;")
    plain.close()
    val bak = File(ctx.cacheDir, "upme_plain_bak.db")
    runCatching { bak.delete() }
    if (dbFile.renameTo(bak)) {
      if (tmp.renameTo(dbFile)) {
        runCatching { bak.delete() }
        setEncryptedFlag(ctx)
      } else runCatching { bak.renameTo(dbFile) }
    }
  } catch (_: Exception) {
    runCatching { tmp.delete() }
  }
}

@Database(entities = [MessageEntity::class, TemanEntity::class, PendingEntity::class], version = 4, exportSchema = false)
abstract class UpMeDb : RoomDatabase() {
  abstract fun messageDao(): MessageDao
  abstract fun temanDao(): TemanDao
  abstract fun pendingDao(): PendingDao
  companion object {
    fun create(ctx: Context): UpMeDb {
      SQLiteDatabase.loadLibs(ctx)
      val passphrase = DbPassphrase.get(ctx)
      // Probe existing encrypted file with current key — if mismatch (old buggy hex), wipe once
      val dbFile = ctx.getDatabasePath("upme.db")
      if (dbFile.exists() && dbFile.length() > 0 && isEncryptedFlag(ctx)) {
        val hex = passphrase.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        var probeOk = false
        try {
          val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, hex, null, SQLiteDatabase.OPEN_READONLY)
          try { db.rawQuery("select count(*) from sqlite_master;", null)?.close() } catch (_: Exception) {}
          db.close(); probeOk = true
        } catch (_: Exception) {
          // try old buggy raw (char array via SupportFactory path) — if also fails, wipe
          try {
            val db2 = SQLiteDatabase.openDatabase(dbFile.absolutePath, String(passphrase), null, SQLiteDatabase.OPEN_READONLY)
            db2.close(); probeOk = true
          } catch (_: Exception) { probeOk = false }
        }
        if (!probeOk) {
          try { ctx.deleteDatabase("upme.db") } catch (_: Exception) {}
          try { dbFile.delete() } catch (_: Exception) {}
          try { File(ctx.cacheDir, "upme_enc_tmp.db").delete() } catch (_: Exception) {}
          try { ctx.getSharedPreferences(ENC_FLAG_PREFS, Context.MODE_PRIVATE).edit().remove(ENC_FLAG_KEY).apply() } catch (_: Exception) {}
          setEncryptedFlag(ctx)
        }
      }
      migratePlaintextIfNeeded(ctx, passphrase)
      val factory: SupportSQLiteOpenHelper.Factory = SupportFactory(passphrase)
      return try {
        Room.databaseBuilder(ctx, UpMeDb::class.java, "upme.db")
          .openHelperFactory(factory)
          .addMigrations(MIGRATION_3_4)
          .fallbackToDestructiveMigrationOnDowngrade()
          .fallbackToDestructiveMigration()
          .build()
      } catch (e: Exception) {
        try { ctx.deleteDatabase("upme.db") } catch (_: Exception) {}
        try { ctx.getSharedPreferences(ENC_FLAG_PREFS, Context.MODE_PRIVATE).edit().remove(ENC_FLAG_KEY).apply() } catch (_: Exception) {}
        setEncryptedFlag(ctx)
        Room.databaseBuilder(ctx, UpMeDb::class.java, "upme.db")
          .openHelperFactory(factory)
          .fallbackToDestructiveMigrationOnDowngrade()
          .fallbackToDestructiveMigration()
          .build()
      }
    }
  }
}
// English naming aliases — convention uniform, keep Indonesian for DB compat
typealias FriendEntity = TemanEntity
typealias FriendDao = TemanDao
typealias PendingRequestEntity = PendingEntity
