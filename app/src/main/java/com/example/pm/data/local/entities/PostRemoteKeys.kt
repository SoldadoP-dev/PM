package com.example.pm.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "post_remote_keys")
data class PostRemoteKeys(
    @PrimaryKey val postId: String,
    val prevKey: String?,
    val nextKey: String?
)
