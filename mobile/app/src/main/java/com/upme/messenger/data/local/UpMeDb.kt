package com.upme.messenger.data.local
import android.content.Context
import androidx.room.*
/** Entity lokal: sumber kebenaran (local-first). Ciphertext opaque, bukan plaintext server. */
@Entity(tableName = "messages", indices = [Index(value = ["conversationId", "createdAt"])])
data class MessageEntity(@PrimaryKey val id: String, val conversationId: String, val cipher: ByteArray, val status: String, val createdAt: Long)

@Entity(tableName = "teman")
data class TemanEntity(@PrimaryKey val pin: String, val name: String, val addedAt: Long)

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
}

@Database(entities = [MessageEntity::class, TemanEntity::class], version = 2, exportSchema = false)
abstract class UpMeDb : RoomDatabase() {
  abstract fun messageDao(): MessageDao
  abstract fun temanDao(): TemanDao
  companion object {
    fun create(ctx: Context): UpMeDb = Room.databaseBuilder(ctx, UpMeDb::class.java, "upme.db").fallbackToDestructiveMigration().build()
  }
}
