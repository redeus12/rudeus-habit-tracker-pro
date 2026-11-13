package com.example.util

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent

object MediaControlHelper {
    private const val TAG = "MediaControlHelper"

    fun sendMediaKey(context: Context, keyCode: Int) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val eventTime = SystemClock.uptimeMillis()
            val downEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0)
            val upEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0)
            audioManager.dispatchMediaKeyEvent(downEvent)
            audioManager.dispatchMediaKeyEvent(upEvent)
            Log.d(TAG, "Dispatched media key event: $keyCode")
        } catch (e: Exception) {
            Log.e(TAG, "Error dispatching media key: $keyCode", e)
        }
    }

    fun togglePlayPause(context: Context) {
        sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    }

    fun play(context: Context) {
        sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY)
    }

    fun pause(context: Context) {
        sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PAUSE)
    }

    fun nextTrack(context: Context) {
        sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    fun previousTrack(context: Context) {
        sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    fun lowerVolume(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
        } catch (e: Exception) {
            Log.e(TAG, "Error lowering volume", e)
        }
    }

    fun raiseVolume(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
        } catch (e: Exception) {
            Log.e(TAG, "Error raising volume", e)
        }
    }

    fun isMusicActive(context: Context): Boolean {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.isMusicActive == true
        } catch (e: Exception) {
            false
        }
    }

    fun openMusicApp(context: Context): Boolean {
        val pm = context.packageManager
        // Check for YouTube Music first
        val ytMusicIntent = pm.getLaunchIntentForPackage("com.google.android.apps.youtube.music")
        if (ytMusicIntent != null) {
            ytMusicIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(ytMusicIntent)
            return true
        }
        // Check for Spotify
        val spotifyIntent = pm.getLaunchIntentForPackage("com.spotify.music")
        if (spotifyIntent != null) {
            spotifyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(spotifyIntent)
            return true
        }
        // Generic music intent
        val genericIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_APP_MUSIC)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return if (genericIntent.resolveActivity(pm) != null) {
            context.startActivity(genericIntent)
            true
        } else {
            false
        }
    }
}
