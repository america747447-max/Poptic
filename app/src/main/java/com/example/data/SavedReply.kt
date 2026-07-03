package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_replies")
data class SavedReply(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String,
    val category: String,
    val useCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
