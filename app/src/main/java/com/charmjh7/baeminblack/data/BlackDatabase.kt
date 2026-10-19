package com.charmjh7.baeminblack.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ShopDao {
    @Transaction @Query("SELECT * FROM shops ORDER BY updatedAt DESC") fun observe(): Flow<List<ShopWithNotes>>
    @Query("SELECT * FROM shops") suspend fun shops(): List<Shop>
    @Query("SELECT * FROM notes") suspend fun notes(): List<Note>
    @Query("SELECT photos FROM notes") suspend fun photoPayloads(): List<String>
    @Query("SELECT * FROM shops WHERE id=:id") suspend fun shop(id: String): Shop?
    @Query("SELECT * FROM shops WHERE shopNo=:no LIMIT 1") suspend fun byNumber(no: String): Shop?
    @Query("SELECT * FROM notes WHERE id=:id") suspend fun note(id: String): Note?
    @Upsert suspend fun put(shop: Shop)
    @Upsert suspend fun put(note: Note)
    @Query("DELETE FROM shops WHERE id=:id") suspend fun deleteShop(id: String)
    @Query("DELETE FROM notes WHERE id=:id") suspend fun deleteNote(id: String)
    @Query("UPDATE shops SET lastRecordDate=(SELECT MAX(date) FROM notes WHERE shopId=:id), updatedAt=:time WHERE id=:id") suspend fun touch(id: String, time: Long)
}
@Database(entities = [Shop::class, Note::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class BlackDatabase : RoomDatabase() { abstract fun dao(): ShopDao }
