package com.example.ui.theme

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) {
    SYSTEM("Follow system"),
    LIGHT("Light mode"),
    DARK("Dark mode")
}

object ThemePreferences {
    private const val PREFS_NAME = "habit_ledger_theme_prefs"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_USE_DYNAMIC_COLOR = "use_dynamic_color"

    private var prefs: SharedPreferences? = null
    private val _themeMode = MutableStateFlow(ThemeMode.LIGHT)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _useDynamicColor = MutableStateFlow(false)
    val useDynamicColor: StateFlow<Boolean> = _useDynamicColor.asStateFlow()

    fun init(context: Context) {
        if (prefs == null) {
            val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs = p
            val saved = p.getString(KEY_THEME_MODE, ThemeMode.LIGHT.name) ?: ThemeMode.LIGHT.name
            _themeMode.value = try {
                ThemeMode.valueOf(saved)
            } catch (e: Exception) {
                ThemeMode.LIGHT
            }
            _useDynamicColor.value = p.getBoolean(KEY_USE_DYNAMIC_COLOR, false)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs?.edit()?.putString(KEY_THEME_MODE, mode.name)?.apply()
    }

    fun setUseDynamicColor(use: Boolean) {
        _useDynamicColor.value = use
        prefs?.edit()?.putBoolean(KEY_USE_DYNAMIC_COLOR, use)?.apply()
    }
}
