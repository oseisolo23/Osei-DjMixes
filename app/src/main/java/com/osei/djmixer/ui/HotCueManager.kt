package com.osei.djmixer.ui

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages Hot Cues for DJ decks.
 * Stores 4 hot cues per track, persists them to SharedPreferences,
 * and restores them whenever the same track is loaded.
 */
class HotCueManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("dj_hot_cues", Context.MODE_PRIVATE)

    fun getCuesForTrack(trackId: String): List<Long?> {
        val raw = prefs.getString("cues_$trackId", null) ?: return listOf(null, null, null, null)
        val parts = raw.split(",")
        return List(4) { i ->
            parts.getOrNull(i)?.toLongOrNull()
        }
    }

    fun setCue(trackId: String, index: Int, positionMs: Long): List<Long?> {
        val current = getCuesForTrack(trackId).toMutableList()
        if (index in 0 until 4) {
            current[index] = positionMs
            saveCues(trackId, current)
        }
        return current
    }

    fun clearCue(trackId: String, index: Int): List<Long?> {
        val current = getCuesForTrack(trackId).toMutableList()
        if (index in 0 until 4) {
            current[index] = null
            saveCues(trackId, current)
        }
        return current
    }

    fun clearAllCues(trackId: String): List<Long?> {
        val cleared = listOf<Long?>(null, null, null, null)
        prefs.edit().remove("cues_$trackId").apply()
        return cleared
    }

    private fun saveCues(trackId: String, cues: List<Long?>) {
        val raw = cues.take(4).joinToString(",") { it?.toString() ?: "" }
        prefs.edit().putString("cues_$trackId", raw).apply()
    }
}
