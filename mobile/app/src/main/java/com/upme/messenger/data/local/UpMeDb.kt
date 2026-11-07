package com.upme.messenger.data.local
import android.content.Context
import androidx.room.*
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

@Database(entities = [MessageEntity::class, TemanEntity::class, PendingEntity::class], version = 3, exportSchema = false)
abstract class UpMeDb : RoomDatabase() {
  abstract fun messageDao(): MessageDao
  abstract fun temanDao(): TemanDao
  abstract fun pendingDao(): PendingDao
  companion object {
    fun create(ctx: Context): UpMeDb = Room.databaseBuilder(ctx, UpMeDb::class.java, "upme.db").fallbackToDestructiveMigration().build()
  }
}
