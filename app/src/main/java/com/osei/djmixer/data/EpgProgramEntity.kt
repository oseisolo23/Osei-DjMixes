package com.osei.djmixer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "epg_programs")
data class EpgProgramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: String, // tvg-id
    val title: String,
    val description: String?,
    val startTime: Long,
    val endTime: Long
)
