package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedReplyDao {
    @Query("SELECT * FROM saved_replies ORDER BY useCount DESC, createdAt DESC")
    fun getAllReplies(): Flow<List<SavedReply>>

    @Query("SELECT * FROM saved_replies WHERE title LIKE :query OR content LIKE :query OR category LIKE :query ORDER BY useCount DESC")
    fun searchReplies(query: String): Flow<List<SavedReply>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReply(reply: SavedReply): Long

    @Update
    suspend fun updateReply(reply: SavedReply)

    @Delete
    suspend fun deleteReply(reply: SavedReply)

    @Query("UPDATE saved_replies SET useCount = useCount + 1 WHERE id = :id")
    suspend fun incrementUseCount(id: Int)
}
