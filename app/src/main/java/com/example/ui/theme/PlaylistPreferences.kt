package com.example.ui.theme

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class PlaylistTrack(
    val uri: String,
    val name: String,
    val isFavorite: Boolean = false
)

enum class MusicSourceMode {
    EXTERNAL,   // Active Media Player (YouTube Music, Spotify, etc.)
    LOCAL,      // In-App Local Playlist
    OFF         // Silent / Disabled
}

object PlaylistPreferences {
    private const val PREFS_NAME = "habit_ledger_playlist_prefs"
    private const val KEY_PLAYLIST = "playlist_tracks"
    private const val KEY_SHUFFLE = "playlist_shuffle"
    private const val KEY_FAVORITES_ONLY = "playlist_favorites_only"
    private const val KEY_MUSIC_SOURCE_MODE = "playlist_music_source_mode"
    private const val KEY_AUTO_PLAY_WITH_TIMER = "playlist_auto_play_with_timer"

    private var prefs: SharedPreferences? = null
    private val _playlist = MutableStateFlow<List<PlaylistTrack>>(emptyList())
    val playlist: StateFlow<List<PlaylistTrack>> = _playlist.asStateFlow()

    private val _shuffle = MutableStateFlow(false)
    val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()

    private val _favoritesOnly = MutableStateFlow(false)
    val favoritesOnly: StateFlow<Boolean> = _favoritesOnly.asStateFlow()

    private val _musicSourceMode = MutableStateFlow(MusicSourceMode.EXTERNAL)
    val musicSourceMode: StateFlow<MusicSourceMode> = _musicSourceMode.asStateFlow()

    private val _autoPlayWithTimer = MutableStateFlow(false)
    val autoPlayWithTimer: StateFlow<Boolean> = _autoPlayWithTimer.asStateFlow()

    fun init(context: Context) {
        if (prefs == null) {
            val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs = p
            _playlist.value = loadPlaylistFromJson(p.getString(KEY_PLAYLIST, "[]") ?: "[]")
            _shuffle.value = p.getBoolean(KEY_SHUFFLE, false)
            _favoritesOnly.value = p.getBoolean(KEY_FAVORITES_ONLY, false)

            val modeStr = p.getString(KEY_MUSIC_SOURCE_MODE, MusicSourceMode.EXTERNAL.name)
            _musicSourceMode.value = try {
                MusicSourceMode.valueOf(modeStr ?: MusicSourceMode.EXTERNAL.name)
            } catch (e: Exception) {
                MusicSourceMode.EXTERNAL
            }

            _autoPlayWithTimer.value = p.getBoolean(KEY_AUTO_PLAY_WITH_TIMER, false)
        }
    }

    private fun loadPlaylistFromJson(jsonStr: String): List<PlaylistTrack> {
        val list = mutableListOf<PlaylistTrack>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val uri = obj.getString("uri")
                val name = obj.getString("name")
                val isFavorite = obj.optBoolean("isFavorite", false)
                list.add(PlaylistTrack(uri, name, isFavorite))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun savePlaylistToJson(list: List<PlaylistTrack>) {
        try {
            val array = JSONArray()
            for (track in list) {
                val obj = JSONObject().apply {
                    put("uri", track.uri)
                    put("name", track.name)
                    put("isFavorite", track.isFavorite)
                }
                array.put(obj)
            }
            prefs?.edit()?.putString(KEY_PLAYLIST, array.toString())?.apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addTracks(newTracks: List<PlaylistTrack>) {
        val current = _playlist.value.toMutableList()
        val filtered = newTracks.filter { nt -> !current.any { it.uri == nt.uri } }
        current.addAll(filtered)
        _playlist.value = current
        savePlaylistToJson(current)
    }

    fun removeTrack(uri: String) {
        val current = _playlist.value.filter { it.uri != uri }
        _playlist.value = current
        savePlaylistToJson(current)
    }

    fun toggleFavorite(uri: String) {
        val current = _playlist.value.map {
            if (it.uri == uri) it.copy(isFavorite = !it.isFavorite) else it
        }
        _playlist.value = current
        savePlaylistToJson(current)
    }

    fun setShuffle(shuffleValue: Boolean) {
        _shuffle.value = shuffleValue
        prefs?.edit()?.putBoolean(KEY_SHUFFLE, shuffleValue)?.apply()
    }

    fun setFavoritesOnly(favoritesOnlyValue: Boolean) {
        _favoritesOnly.value = favoritesOnlyValue
        prefs?.edit()?.putBoolean(KEY_FAVORITES_ONLY, favoritesOnlyValue)?.apply()
    }

    fun setMusicSourceMode(mode: MusicSourceMode) {
        _musicSourceMode.value = mode
        prefs?.edit()?.putString(KEY_MUSIC_SOURCE_MODE, mode.name)?.apply()
    }

    fun setAutoPlayWithTimer(autoPlay: Boolean) {
        _autoPlayWithTimer.value = autoPlay
        prefs?.edit()?.putBoolean(KEY_AUTO_PLAY_WITH_TIMER, autoPlay)?.apply()
    }
}
