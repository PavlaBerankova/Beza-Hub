package com.berpod.app.data.repository

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The light/dark choice. Until the user flips it, [darkMode] is null and the app follows the phone's setting. */
class ThemeSettings(private val prefs: SharedPreferences) {

    private val _darkMode = MutableStateFlow(if (prefs.contains(KEY)) prefs.getBoolean(KEY, false) else null)
    val darkMode: StateFlow<Boolean?> = _darkMode.asStateFlow()

    fun setDarkMode(dark: Boolean) {
        prefs.edit().putBoolean(KEY, dark).apply()
        _darkMode.value = dark
    }

    private companion object {
        const val KEY = "dark_mode"
    }
}
