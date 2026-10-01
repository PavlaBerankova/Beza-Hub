package com.berpod.app.data.repository

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where the queue's selected tag survives a restart. */
interface QueueFilterStorage {
    fun read(): Long?
    fun write(tagId: Long?)
}

class PrefsQueueFilterStorage(private val prefs: SharedPreferences) : QueueFilterStorage {
    override fun read(): Long? = prefs.getLong(KEY, NONE).takeIf { it != NONE }

    override fun write(tagId: Long?) {
        prefs.edit().putLong(KEY, tagId ?: NONE).apply()
    }

    private companion object {
        const val KEY = "selectedTagId"
        const val NONE = -1L
    }
}

/**
 * The tag the Fronta is filtered by. The queue screen and playback read the same one, so "další"
 * and auto-advance stay inside the filter; it is remembered between runs.
 */
class QueueFilterRepository(private val storage: QueueFilterStorage) {

    private val _selectedTagId = MutableStateFlow(storage.read())
    /** null = "Všechny". */
    val selectedTagId: StateFlow<Long?> = _selectedTagId.asStateFlow()

    fun select(tagId: Long?) {
        if (_selectedTagId.value == tagId) return
        _selectedTagId.value = tagId
        storage.write(tagId)
    }
}
