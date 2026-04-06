package com.lumus.vkapp.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM connection_profiles ORDER BY updatedAtMillis DESC")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM connection_profiles WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: ProfileEntity): Long

    @Delete
    suspend fun delete(entity: ProfileEntity)
}

