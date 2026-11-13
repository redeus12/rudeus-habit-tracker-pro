package com.example.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppPreferences {
    private const val PREFS_NAME = "habit_ledger_app_prefs"
    private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
    private const val KEY_SOUND_EFFECTS_ENABLED = "sound_effects_enabled"
    private const val KEY_HAPTICS_ENABLED = "haptics_enabled"
    private const val KEY_CUSTOM_SOUND_NAME = "custom_sound_name"
    private const val KEY_CUSTOM_SOUND_PATH = "custom_sound_path"

    private var prefs: SharedPreferences? = null

    private val _isOnboardingCompleted = MutableStateFlow(false)
    val isOnboardingCompleted: StateFlow<Boolean> = _isOnboardingCompleted.asStateFlow()

    private val _isSoundEffectsEnabled = MutableStateFlow(true)
    val isSoundEffectsEnabled: StateFlow<Boolean> = _isSoundEffectsEnabled.asStateFlow()

    private val _isHapticsEnabled = MutableStateFlow(true)
    val isHapticsEnabled: StateFlow<Boolean> = _isHapticsEnabled.asStateFlow()

    private val _customSoundName = MutableStateFlow<String?>(null)
    val customSoundName: StateFlow<String?> = _customSoundName.asStateFlow()

    var customSoundPath: String? = null
        private set

    fun init(context: Context) {
        if (prefs == null) {
            val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs = p
            _isOnboardingCompleted.value = p.getBoolean(KEY_ONBOARDING_COMPLETED, false)
            _isSoundEffectsEnabled.value = p.getBoolean(KEY_SOUND_EFFECTS_ENABLED, true)
            _isHapticsEnabled.value = p.getBoolean(KEY_HAPTICS_ENABLED, true)
            _customSoundName.value = p.getString(KEY_CUSTOM_SOUND_NAME, null)
            customSoundPath = p.getString(KEY_CUSTOM_SOUND_PATH, null)
        }
    }

    fun setCustomSound(name: String, path: String) {
        _customSoundName.value = name
        customSoundPath = path
        prefs?.edit()
            ?.putString(KEY_CUSTOM_SOUND_NAME, name)
            ?.putString(KEY_CUSTOM_SOUND_PATH, path)
            ?.apply()
    }

    fun clearCustomSound() {
        _customSoundName.value = null
        customSoundPath = null
        prefs?.edit()
            ?.remove(KEY_CUSTOM_SOUND_NAME)
            ?.remove(KEY_CUSTOM_SOUND_PATH)
            ?.apply()
    }

    fun setOnboardingCompleted(completed: Boolean) {
        _isOnboardingCompleted.value = completed
        prefs?.edit()?.putBoolean(KEY_ONBOARDING_COMPLETED, completed)?.apply()
    }

    fun setSoundEffectsEnabled(enabled: Boolean) {
        _isSoundEffectsEnabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_SOUND_EFFECTS_ENABLED, enabled)?.apply()
    }

    fun setHapticsEnabled(enabled: Boolean) {
        _isHapticsEnabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_HAPTICS_ENABLED, enabled)?.apply()
    }
}
