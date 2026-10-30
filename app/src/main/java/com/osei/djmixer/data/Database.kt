package com.osei.djmixer.data

import com.osei.djmixer.ui.theme.*

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT * FROM media_items ORDER BY addedAt DESC")
    fun getAllMedia(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE isVideo = 0 AND uri NOT LIKE 'http%' ORDER BY addedAt DESC")
    fun getAllAudio(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE isVideo = 1 AND uri NOT LIKE 'http%' ORDER BY addedAt DESC")
    fun getAllVideo(): Flow<List<MediaItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: MediaItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaList(mediaList: List<MediaItemEntity>)

    @Query("DELETE FROM media_items")
    suspend fun clearAllMedia()

    // Favorites
    @Query("SELECT media_items.* FROM media_items INNER JOIN favorites ON media_items.id = favorites.mediaId")
    fun getFavorites(): Flow<List<MediaItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE mediaId = :mediaId")
    suspend fun removeFavorite(mediaId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE mediaId = :mediaId)")
    fun isFavorite(mediaId: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE mediaId = :mediaId)")
    suspend fun isFavoriteSingle(mediaId: String): Boolean

    // History
    @Query("SELECT media_items.* FROM media_items INNER JOIN player_history ON media_items.id = player_history.mediaId ORDER BY playedAt DESC LIMIT 50")
    fun getRecentHistory(): Flow<List<MediaItemEntity>>
    
    @Query("SELECT media_items.* FROM media_items INNER JOIN player_history ON media_items.id = player_history.mediaId WHERE title LIKE '%prayer%' OR category LIKE '%prayer%' ORDER BY playedAt DESC LIMIT 20")
    suspend fun getRecentPrayersList(): List<MediaItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToHistory(history: PlayerHistoryEntity)

    @Query("DELETE FROM player_history WHERE mediaId = :mediaId")
    suspend fun deleteHistoryForMedia(mediaId: String)

    @Query("SELECT lastPosition FROM player_history WHERE mediaId = :mediaId ORDER BY playedAt DESC LIMIT 1")
    suspend fun getLastPositionForMediaSingle(mediaId: String): Long?

    @Query("SELECT * FROM player_history ORDER BY playedAt DESC")
    fun getAllHistoryFlow(): Flow<List<PlayerHistoryEntity>>

    // Playlists
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun createPlaylist(playlist: PlaylistEntity): Long

    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addMediaToPlaylist(crossRef: PlaylistMediaCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistMediaCrossRefs(crossRefs: List<PlaylistMediaCrossRef>)

    @Query("""
        SELECT media_items.* FROM media_items 
        INNER JOIN playlist_media ON media_items.id = playlist_media.mediaId 
        WHERE playlist_media.playlistId = :playlistId 
        ORDER BY playlist_media.orderIndex ASC
    """)
    fun getMediaForPlaylist(playlistId: Long): Flow<List<MediaItemEntity>>

    @Query("DELETE FROM playlist_media WHERE playlistId = :playlistId AND mediaId = :mediaId")
    suspend fun removeMediaFromPlaylist(playlistId: Long, mediaId: String)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("SELECT * FROM media_items WHERE category IN ('Streams', 'Football', 'Highlights', 'TV', 'Prayer', 'Radio') ORDER BY addedAt DESC")
    fun getAllStreams(): Flow<List<MediaItemEntity>>

    @Query("DELETE FROM media_items WHERE id = :mediaId")
    suspend fun deleteMedia(mediaId: String)

    @Query("DELETE FROM media_items WHERE category != 'Streams'")
    suspend fun clearLocalMedia()

    @Query("UPDATE media_items SET artUri = REPLACE(REPLACE(artUri, 'w=350', 'w=1080'), 'w=200', 'w=1080') WHERE artUri LIKE '%w=350%' OR artUri LIKE '%w=200%'")
    suspend fun updateImageQuality()

    @Query("DELETE FROM media_items WHERE id IN ('tv_france24_en', 'tv_aljazeera_en', 'tv_redbull_tv', 'tv_nasa_tv', 'tv_bloomberg', 'tv_dw_english', 'tv_trt_world', 'tv_cgtn', 'stream_klove', 'stream_air1', 'stream_twr', 'stream_awr', 'stream_hope_channel', 'stream_3abn', 'stream_premier_christian', 'stream_ucb', 'stream_ewtn', 'stream_bible_is', 'stream_premier_gospel', 'stream_praise_and_worship', 'stream_vision1_fm', 'stream_angel_fm', 'stream_joy_fm', 'stream_peace_fm', 'stream_citi_fm', 'stream_oman_fm', 'stream_adom_fm', 'stream_yfm', 'stream_bbc_world', 'stream_capital_xtra')")
    suspend fun removeOldTvDefaults()

    @Query("SELECT COUNT(*) FROM media_items WHERE category IN ('Streams', 'Football', 'Highlights', 'TV', 'Prayer')")
    suspend fun getStreamsCount(): Int

    @Query("UPDATE media_items SET bpm = :bpm WHERE id = :mediaId")
    suspend fun updateMediaBpm(mediaId: String, bpm: Int?)

    @Query("SELECT * FROM media_items WHERE id = :mediaId LIMIT 1")
    suspend fun getMediaByIdSingle(mediaId: String): MediaItemEntity?

    @Query("UPDATE media_items SET title = :newTitle WHERE id = :mediaId")
    suspend fun renameMedia(mediaId: String, newTitle: String)

    @Query("UPDATE media_items SET isPrivate = :isPrivate WHERE id = :mediaId")
    suspend fun setMediaPrivacy(mediaId: String, isPrivate: Boolean)

    @Query("SELECT * FROM media_items WHERE isPrivate = 1 ORDER BY addedAt DESC")
    fun getPrivateMedia(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' ORDER BY addedAt DESC")
    fun searchMedia(query: String): Flow<List<MediaItemEntity>>

    // Track Analysis Cache
    @Query("SELECT * FROM track_analysis WHERE trackId = :trackId LIMIT 1")
    suspend fun getTrackAnalysis(trackId: String): TrackAnalysisEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackAnalysis(analysis: TrackAnalysisEntity)

    @Query("DELETE FROM track_analysis WHERE trackId = :trackId")
    suspend fun deleteTrackAnalysis(trackId: String)

    @Query("DELETE FROM track_analysis WHERE analysisVersion != :currentVersion")
    suspend fun clearIncompatibleAnalysis(currentVersion: Int)
}

@Database(
    entities = [
        MediaItemEntity::class,
        PlayerHistoryEntity::class,
        FavoriteEntity::class,
        PlaylistEntity::class,
        PlaylistMediaCrossRef::class,
        EpgProgramEntity::class,
        TrackAnalysisEntity::class
    ],
    version = 13,
    exportSchema = false
)
abstract class DJMixerDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun epgDao(): EpgDao

    companion object {
        val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `track_analysis` (
                        `trackId` TEXT NOT NULL PRIMARY KEY,
                        `fileLastModified` INTEGER NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `bpm` INTEGER,
                        `bpmConfidence` REAL NOT NULL,
                        `musicalKey` TEXT,
                        `keyConfidence` REAL NOT NULL,
                        `energyScore` REAL NOT NULL,
                        `energyConfidence` REAL NOT NULL,
                        `energyLevel` TEXT NOT NULL,
                        `analysisVersion` INTEGER NOT NULL,
                        `analyzedAt` INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_12_13 = object : androidx.room.migration.Migration(12, 13) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `track_analysis_new` (
                        `trackId` TEXT NOT NULL PRIMARY KEY,
                        `fileLastModified` INTEGER NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `bpm` INTEGER,
                        `bpmConfidence` REAL NOT NULL,
                        `bpmSource` TEXT NOT NULL DEFAULT 'UNKNOWN',
                        `musicalKey` TEXT,
                        `keyConfidence` REAL NOT NULL,
                        `keySource` TEXT NOT NULL DEFAULT 'UNKNOWN',
                        `energyScore` REAL,
                        `energyConfidence` REAL NOT NULL,
                        `energyLevel` TEXT NOT NULL,
                        `analysisVersion` INTEGER NOT NULL,
                        `analyzedAt` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT OR IGNORE INTO `track_analysis_new` (`trackId`, `fileLastModified`, `durationMs`, `bpm`, `bpmConfidence`, `bpmSource`, `musicalKey`, `keyConfidence`, `keySource`, `energyScore`, `energyConfidence`, `energyLevel`, `analysisVersion`, `analyzedAt`)
                    SELECT `trackId`, `fileLastModified`, `durationMs`, `bpm`, `bpmConfidence`, 'UNKNOWN', `musicalKey`, `keyConfidence`, 'UNKNOWN', `energyScore`, `energyConfidence`, `energyLevel`, `analysisVersion`, `analyzedAt` FROM `track_analysis`
                """.trimIndent())
                db.execSQL("DROP TABLE `track_analysis`")
                db.execSQL("ALTER TABLE `track_analysis_new` RENAME TO `track_analysis`")
            }
        }
    }
}
