package com.osei.djmixer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EpgDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrograms(programs: List<EpgProgramEntity>)

    @Query("DELETE FROM epg_programs")
    suspend fun clearAll()

    @Query("DELETE FROM epg_programs WHERE endTime < :time")
    suspend fun deleteOldPrograms(time: Long)

    @Query("SELECT * FROM epg_programs WHERE channelId = :channelId AND startTime <= :currentTime AND endTime > :currentTime ORDER BY startTime ASC LIMIT 1")
    fun getCurrentProgramFlow(channelId: String, currentTime: Long): Flow<EpgProgramEntity?>

    @Query("SELECT * FROM epg_programs WHERE channelId = :channelId AND startTime > :currentTime ORDER BY startTime ASC LIMIT 1")
    fun getNextProgramFlow(channelId: String, currentTime: Long): Flow<EpgProgramEntity?>

    @Query("SELECT * FROM epg_programs WHERE channelId = :channelId ORDER BY startTime ASC")
    fun getScheduleForChannel(channelId: String): Flow<List<EpgProgramEntity>>
    
    @Query("SELECT * FROM epg_programs WHERE channelId = :channelId AND startTime <= :currentTime AND endTime > :currentTime ORDER BY startTime ASC LIMIT 1")
    suspend fun getCurrentProgram(channelId: String, currentTime: Long): EpgProgramEntity?
}
