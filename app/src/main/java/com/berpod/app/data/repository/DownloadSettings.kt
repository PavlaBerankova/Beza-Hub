package com.berpod.app.data.repository

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The download choices; all are on until the user turns them off. */
class DownloadSettings(private val prefs: SharedPreferences) {

    private val _wifiOnly = MutableStateFlow(prefs.getBoolean(KEY_WIFI_ONLY, true))
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()

    private val _deleteAfterPlayed = MutableStateFlow(prefs.getBoolean(KEY_DELETE_AFTER_PLAYED, true))
    val deleteAfterPlayed: StateFlow<Boolean> = _deleteAfterPlayed.asStateFlow()

    private val _autoDownloadOnQueue = MutableStateFlow(prefs.getBoolean(KEY_AUTO_ON_QUEUE, true))
    val autoDownloadOnQueue: StateFlow<Boolean> = _autoDownloadOnQueue.asStateFlow()

    fun setAutoDownloadOnQueue(value: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_ON_QUEUE, value).apply()
        _autoDownloadOnQueue.value = value
    }

    fun setWifiOnly(value: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY, value).apply()
        _wifiOnly.value = value
    }

    fun setDeleteAfterPlayed(value: Boolean) {
        prefs.edit().putBoolean(KEY_DELETE_AFTER_PLAYED, value).apply()
        _deleteAfterPlayed.value = value
    }

    private companion object {
        const val KEY_AUTO_ON_QUEUE = "downloads_auto_on_queue"
        const val KEY_WIFI_ONLY = "downloads_wifi_only"
        const val KEY_DELETE_AFTER_PLAYED = "downloads_delete_after_played"
    }
}
