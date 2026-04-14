package com.example.pm.data.local.entities

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PostRemoteKeysDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(remoteKey: List<PostRemoteKeys>)

    @Query("SELECT * FROM post_remote_keys WHERE postId = :postId")
    suspend fun remoteKeysPostId(postId: String): PostRemoteKeys?

    @Query("DELETE FROM post_remote_keys")
    suspend fun clearRemoteKeys()
}
