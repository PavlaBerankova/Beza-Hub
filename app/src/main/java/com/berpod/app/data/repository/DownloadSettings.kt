package com.berpod.app.data.repository

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The two download choices; both are on until the user turns them off. */
class DownloadSettings(private val prefs: SharedPreferences) {

    private val _wifiOnly = MutableStateFlow(prefs.getBoolean(KEY_WIFI_ONLY, true))
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()

    private val _deleteAfterPlayed = MutableStateFlow(prefs.getBoolean(KEY_DELETE_AFTER_PLAYED, true))
    val deleteAfterPlayed: StateFlow<Boolean> = _deleteAfterPlayed.asStateFlow()

    fun setWifiOnly(value: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY, value).apply()
        _wifiOnly.value = value
    }

    fun setDeleteAfterPlayed(value: Boolean) {
        prefs.edit().putBoolean(KEY_DELETE_AFTER_PLAYED, value).apply()
        _deleteAfterPlayed.value = value
    }

    private companion object {
        const val KEY_WIFI_ONLY = "downloads_wifi_only"
        const val KEY_DELETE_AFTER_PLAYED = "downloads_delete_after_played"
    }
}
