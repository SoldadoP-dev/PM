package com.example.pm.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.pm.data.local.entities.PostEntity
import com.example.pm.data.local.entities.PostRemoteKeys
import com.example.pm.data.local.entities.PostRemoteKeysDao

@Database(
    entities = [PostEntity::class, PostRemoteKeys::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun postDao(): PostDao
    abstract fun remoteKeysDao(): PostRemoteKeysDao
}
