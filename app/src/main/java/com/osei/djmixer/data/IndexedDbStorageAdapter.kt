package com.osei.djmixer.data

import com.osei.djmixer.ui.theme.*

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * An adapter mimicking the W3C Web IndexedDB specification for Kotlin/Android.
 * Maps transactional Object Stores ("playlists", "favorites", "metadata") to Room databases,
 * ensuring standard async on-device Offline Storage operates smoothly.
 */
class IndexedDbStorageAdapter(private val mediaDao: MediaDao) {

    companion object {
        const val DB_NAME = "OseiDjMixesDB"
        const val VERSION = 1
    }

    suspend fun open(): IndexedDbConnection {
        return IndexedDbConnection(mediaDao)
    }
}

class IndexedDbConnection(private val mediaDao: MediaDao) {

    suspend fun transaction(
        storeNames: List<String>,
        mode: String = "readwrite",
        block: suspend IndexedDbTransaction.() -> Unit
    ) {
        withContext(Dispatchers.IO) {
            val transaction = IndexedDbTransaction(storeNames, mode, mediaDao)
            block(transaction)
        }
    }
}

class IndexedDbTransaction(
    val storeNames: List<String>,
    val mode: String,
    private val mediaDao: MediaDao
) {
    fun objectStore(name: String): IndexedDbObjectStore {
        if (!storeNames.contains(name)) {
            throw IllegalArgumentException("Store '$name' was not opened in this transaction scope.")
        }
        return IndexedDbObjectStore(name, mediaDao)
    }
}

class IndexedDbObjectStore(
    private val storeName: String,
    private val mediaDao: MediaDao
) {
    /**
     * Store (put) a record matching IndexedDB's key-value interface.
     */
    suspend fun put(key: String, value: String): Boolean {
        return when (storeName) {
            "favorites" -> {
                mediaDao.addFavorite(FavoriteEntity(mediaId = key))
                true
            }
            "playlists" -> {
                // If it's a playlist, we create a new Playlist record
                mediaDao.createPlaylist(PlaylistEntity(name = value)) > 0
            }
            "metadata" -> {
                // Ignore or perform log
                true
            }
            else -> false
        }
    }

    /**
     * Retrieve a record from the database.
     */
    suspend fun get(key: String): String? {
        return when (storeName) {
            "favorites" -> {
                val exists = mediaDao.isFavoriteSingle(key)
                if (exists) "favorited" else null
            }
            else -> null
        }
    }

    /**
     * Delete a record by key.
     */
    suspend fun delete(key: String): Boolean {
        return when (storeName) {
            "favorites" -> {
                mediaDao.removeFavorite(key)
                true
            }
            "playlists" -> {
                val id = key.toLongOrNull() ?: return false
                mediaDao.deletePlaylist(id)
                true
            }
            else -> false
        }
    }

    /**
     * Retrieve all keys or values in the store.
     */
    suspend fun getAll(): List<String> {
        return when (storeName) {
            "favorites" -> {
                // Retrieve list of favorites from Flow
                val favoritesList = mediaDao.getFavorites().first()
                favoritesList.map { it.id }
            }
            "playlists" -> {
                val playlistsList = mediaDao.getAllPlaylists().first()
                playlistsList.map { "${it.id}: ${it.name}" }
            }
            else -> emptyList()
        }
    }
}
