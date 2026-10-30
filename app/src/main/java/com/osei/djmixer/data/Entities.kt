package com.osei.djmixer.data

import com.osei.djmixer.ui.theme.*

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_items",
    indices = [
        Index(value = ["title"]),
        Index(value = ["artist"]),
        Index(value = ["category"])
    ]
)
data class MediaItemEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String?,
    val album: String? = null,
    val uri: String,
    val artUri: String?,
    val duration: Long,
    val isVideo: Boolean,
    val category: String = "Uncategorized",
    val addedAt: Long = System.currentTimeMillis(),
    val isPrivate: Boolean = false,
    val region: String? = null,
    val bpm: Int? = null,
    val tvgId: String? = null
)

@Entity(tableName = "player_history")
data class PlayerHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaId: String,
    val playedAt: Long = System.currentTimeMillis(),
    val lastPosition: Long = 0
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val mediaId: String,
    val favoritedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_media",
    primaryKeys = ["playlistId", "mediaId"]
)
data class PlaylistMediaCrossRef(
    val playlistId: Long,
    val mediaId: String,
    val orderIndex: Int
)

@Entity(tableName = "track_analysis")
data class TrackAnalysisEntity(
    @PrimaryKey val trackId: String,
    val fileLastModified: Long,
    val durationMs: Long,
    val bpm: Int?,
    val bpmConfidence: Float,
    val bpmSource: String = "UNKNOWN",
    val musicalKey: String?, // e.g. "Am", "C", "F#m", or null for UNKNOWN
    val keyConfidence: Float,
    val keySource: String = "UNKNOWN",
    val energyScore: Float?, // null if UNKNOWN
    val energyConfidence: Float,
    val energyLevel: String, // "LOW", "MEDIUM", "HIGH", or "UNKNOWN"
    val analysisVersion: Int,
    val analyzedAt: Long = System.currentTimeMillis()
)



