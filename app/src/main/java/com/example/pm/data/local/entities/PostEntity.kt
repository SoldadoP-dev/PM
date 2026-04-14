package com.example.pm.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "posts")
data class PostEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val username: String,
    val userPhotoUrl: String,
    val imageUrl: String,
    val caption: String,
    val timestamp: Long,
    val likesCount: Int,
    val isLikedByMe: Boolean
)
