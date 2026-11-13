package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.example.R

object AppFeedbackHelper {
    private const val TAG = "AppFeedbackHelper"
    private var soundPool: SoundPool? = null
    private var soundId: Int = 0
    private var isSoundLoaded: Boolean = false
    private var activeMediaPlayer: MediaPlayer? = null

    @Synchronized
    fun init(context: Context) {
        if (soundPool != null) return
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            soundPool = SoundPool.Builder()
                .setMaxStreams(5)
                .setAudioAttributes(audioAttributes)
                .build().apply {
                    setOnLoadCompleteListener { _, sampleId, status ->
                        if (status == 0 && sampleId == soundId) {
                            isSoundLoaded = true
                            Log.d(TAG, "Custom audio file loaded successfully into SoundPool.")
                        }
                    }
                    soundId = load(context.applicationContext, R.raw.task_sound, 1)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SoundPool", e)
        }
    }

    private fun ensureInitialized(context: Context) {
        if (soundPool == null) {
            init(context)
        }
    }

    private fun getVibrator(context: Context): Vibrator? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (e: Exception) {
            null
        }
    }

    fun vibrateClick(context: Context) {
        if (!AppPreferences.isHapticsEnabled.value) return
        try {
            val vibrator = getVibrator(context) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(30)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration failed", e)
        }
    }

    fun vibrateSuccess(context: Context) {
        if (!AppPreferences.isHapticsEnabled.value) return
        try {
            val vibrator = getVibrator(context) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 45, 60, 55)
                val amplitudes = intArrayOf(0, 180, 0, 240)
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 45, 60, 55), -1)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Success vibration failed", e)
        }
    }

    fun vibrateCelebration(context: Context) {
        if (!AppPreferences.isHapticsEnabled.value) return
        try {
            val vibrator = getVibrator(context) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 40, 50, 60, 50, 90)
                val amplitudes = intArrayOf(0, 140, 0, 200, 0, 255)
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 40, 50, 60, 50, 90), -1)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Celebration vibration failed", e)
        }
    }

    fun playSound(context: Context) {
        if (!AppPreferences.isSoundEffectsEnabled.value) return
        try {
            val customPath = AppPreferences.customSoundPath
            if (customPath != null) {
                val soundFile = java.io.File(customPath)
                if (soundFile.exists() && soundFile.length() > 0) {
                    try {
                        activeMediaPlayer?.release()
                    } catch (e: Exception) {}
                    val mp = MediaPlayer()
                    mp.setDataSource(soundFile.absolutePath)
                    mp.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    mp.prepare()
                    mp.setOnCompletionListener {
                        try { it.release() } catch (e: Exception) {}
                        if (activeMediaPlayer == it) {
                            activeMediaPlayer = null
                        }
                    }
                    mp.start()
                    activeMediaPlayer = mp
                    return
                }
            }

            ensureInitialized(context)
            if (isSoundLoaded && soundId != 0) {
                soundPool?.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f)
            } else {
                // Fallback MediaPlayer if SoundPool is still preloading
                val mp = MediaPlayer.create(context.applicationContext, R.raw.task_sound)
                mp?.setOnCompletionListener { 
                    try { it.release() } catch (e: Exception) {} 
                }
                mp?.start()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not play custom task sound", e)
        }
    }

    fun previewSound(context: Context) {
        try {
            val customPath = AppPreferences.customSoundPath
            if (customPath != null) {
                val soundFile = java.io.File(customPath)
                if (soundFile.exists() && soundFile.length() > 0) {
                    try {
                        activeMediaPlayer?.release()
                    } catch (e: Exception) {}
                    val mp = MediaPlayer()
                    mp.setDataSource(soundFile.absolutePath)
                    mp.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    mp.prepare()
                    mp.setOnCompletionListener {
                        try { it.release() } catch (e: Exception) {}
                        if (activeMediaPlayer == it) {
                            activeMediaPlayer = null
                        }
                    }
                    mp.start()
                    activeMediaPlayer = mp
                    return
                }
            }

            val mp = MediaPlayer.create(context.applicationContext, R.raw.task_sound)
            mp?.setOnCompletionListener { 
                try { it.release() } catch (e: Exception) {} 
            }
            mp?.start()
        } catch (e: Exception) {
            Log.w(TAG, "Could not preview sound", e)
        }
    }

    fun triggerClickFeedback(context: Context) {
        vibrateClick(context)
        playSound(context)
    }

    fun triggerCompletionFeedback(context: Context) {
        vibrateSuccess(context)
        playSound(context)
    }

    fun triggerCelebrationFeedback(context: Context) {
        vibrateCelebration(context)
        playSound(context)
    }
}
