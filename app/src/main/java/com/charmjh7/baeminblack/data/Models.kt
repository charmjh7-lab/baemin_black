package com.charmjh7.baeminblack.data

import androidx.room.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
enum class ShopStatus(val label: String) { NORMAL("일반"), CAUTION("주의"), BLOCKED("블랙리스트"), FAVORITE("즐겨찾기") }
@Serializable
@Entity(tableName = "shops", indices = [Index(value = ["shopNo"], unique = true)])
data class Shop(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val shopNo: String? = null,
    val name: String,
    val branch: String = "",
    val aliases: List<String> = emptyList(),
    val shareLink: String = "",
    val shareText: String = "",
    val status: ShopStatus = ShopStatus.NORMAL,
    val lastRecordDate: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
@Serializable
@Entity(tableName = "notes", foreignKeys = [ForeignKey(entity = Shop::class, parentColumns = ["id"], childColumns = ["shopId"], onDelete = ForeignKey.CASCADE)], indices = [Index("shopId")])
data class Note(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val shopId: String,
    val date: String,
    val menu: String = "",
    val title: String = "",
    val body: String = "",
    val rating: Int? = null,
    val photos: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
data class ShopWithNotes(@Embedded val shop: Shop, @Relation(parentColumn = "id", entityColumn = "shopId") val notes: List<Note>)
class Converters {
    @TypeConverter fun strings(value: List<String>): String = Json.encodeToString(value)
    @TypeConverter fun list(value: String): List<String> = Json.decodeFromString(value)
    @TypeConverter fun status(value: ShopStatus): String = value.name
    @TypeConverter fun status(value: String): ShopStatus = ShopStatus.valueOf(value)
}
