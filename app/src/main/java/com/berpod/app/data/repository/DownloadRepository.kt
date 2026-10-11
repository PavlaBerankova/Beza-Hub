package com.berpod.app.data.repository

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.berpod.app.data.local.DownloadStatus
import com.berpod.app.data.local.EpisodeDownloadDao
import com.berpod.app.data.local.EpisodeDownloadEntity
import com.berpod.app.download.DownloadWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

/** What a tap on a download button did: asked for the episode, called the download off, or deleted the finished copy. */
enum class DownloadToggle { Started, Cancelled, Removed }

/** How many episodes are stored for offline listening and how much room they take. */
data class DownloadStorage(val count: Int, val bytes: Long)

/** Offline copies of episodes: asks for them, tracks them, deletes them and says where a finished one lives. */
class DownloadRepository(
    context: Context,
    private val dao: EpisodeDownloadDao,
    private val settings: DownloadSettings,
    private val dir: File
) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _downloads = MutableStateFlow<Map<Long, EpisodeDownloadEntity>>(emptyMap())
    /** Every episode that has a download row, by episode id. */
    val downloads: StateFlow<Map<Long, EpisodeDownloadEntity>> = _downloads.asStateFlow()

    val storage = downloads.map { all ->
        val done = all.values.filter { it.status == DownloadStatus.COMPLETED }
        DownloadStorage(done.size, done.sumOf { it.totalBytes ?: 0L })
    }

    init {
        dao.observeAll().onEach { list -> _downloads.value = list.associateBy { it.episodeId } }.launchIn(scope)
        // "Delete after playing": a finished copy goes once its episode has been played.
        combine(settings.deleteAfterPlayed, dao.observePlayedCompleted()) { enabled, played -> if (enabled) played else emptyList() }
            .onEach { played -> played.forEach { remove(it.episodeId) } }
            .launchIn(scope)
        scope.launch { reconcile() }
    }

    /** The finished file of the episode, when there is one on disk. */
    fun localFileFor(episodeId: Long): File? {
        val row = _downloads.value[episodeId] ?: return null
        if (row.status != DownloadStatus.COMPLETED) return null
        return row.filePath?.let(::File)?.takeIf { it.exists() }
    }

    suspend fun download(episodeId: Long) = downloadAll(listOf(episodeId))

    /** One button, one action for the state the episode is in: download (or retry), cancel, or delete the copy. */
    suspend fun toggle(episodeId: Long): DownloadToggle = when (dao.get(episodeId)?.status) {
        null, DownloadStatus.FAILED -> { download(episodeId); DownloadToggle.Started }
        DownloadStatus.COMPLETED -> { remove(episodeId); DownloadToggle.Removed }
        DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING -> { remove(episodeId); DownloadToggle.Cancelled }
    }

    /** Queues the episodes in the given order; those already queued, running or finished are left alone. */
    suspend fun downloadAll(episodeIds: List<Long>) {
        val now = System.currentTimeMillis()
        val existing = dao.getAll().associateBy { it.episodeId }
        val fresh = episodeIds.filter { existing[it] == null }
        // A failed one is asked for again; its partial file lets it carry on.
        episodeIds.filter { existing[it]?.status == DownloadStatus.FAILED }.forEachIndexed { i, id ->
            dao.upsert(existing.getValue(id).copy(status = DownloadStatus.QUEUED, queuedAtMs = now + i))
        }
        dao.insertIfAbsent(
            fresh.mapIndexed { i, id ->
                EpisodeDownloadEntity(id, DownloadStatus.QUEUED, 0L, null, null, now + i, null)
            }
        )
        if (dao.hasPending()) enqueue(ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    /** Cancels a running or waiting download, or deletes a finished one. */
    suspend fun remove(episodeId: Long) {
        dao.delete(episodeId)
        deleteFilesOf(episodeId)
    }

    /** Deletes the offline copies (and download rows) of just these episodes, e.g. those of a podcast being deleted. */
    suspend fun removeDownloadsOf(episodeIds: Set<Long>) {
        dao.getAll().filter { it.episodeId in episodeIds }.forEach { remove(it.episodeId) }
    }

    suspend fun removeAll() {
        WorkManager.getInstance(appContext).cancelUniqueWork(DownloadWorker.UNIQUE_NAME)
        dao.deleteAll()
        dir.listFiles()?.forEach { it.delete() }
    }

    /** Applies a changed Wi-Fi choice to downloads that are already waiting. */
    suspend fun onWifiOnlyChanged() {
        if (dao.hasPending()) enqueue(ExistingWorkPolicy.REPLACE)
    }

    private fun enqueue(policy: ExistingWorkPolicy) {
        val network = if (settings.wifiOnly.value) NetworkType.UNMETERED else NetworkType.CONNECTED
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(DownloadWorker.UNIQUE_NAME, policy, request)
    }

    private fun deleteFilesOf(episodeId: Long) {
        dir.listFiles { f -> f.name.substringBefore('.') == episodeId.toString() }?.forEach { it.delete() }
    }

    /** After a restart: finished rows whose file is gone are dropped, and anything left "running" waits again. */
    private suspend fun reconcile() {
        dao.getAll().forEach { row ->
            when {
                row.status == DownloadStatus.COMPLETED && row.filePath?.let { File(it).exists() } != true -> dao.delete(row.episodeId)
                row.status == DownloadStatus.DOWNLOADING -> dao.setStatus(row.episodeId, DownloadStatus.QUEUED)
            }
        }
    }
}
