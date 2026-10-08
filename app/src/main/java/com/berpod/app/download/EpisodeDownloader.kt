package com.berpod.app.download

import com.berpod.app.data.local.EpisodeDao
import com.berpod.app.data.local.EpisodeDownloadDao
import com.berpod.app.data.repository.ResponseAction
import com.berpod.app.data.repository.classifyResponse
import com.berpod.app.data.repository.downloadFileName
import com.berpod.app.data.repository.rangeHeader
import com.berpod.app.data.repository.totalFromContentRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.coroutineContext

enum class DownloadOutcome {
    Done,
    /** A network or server hiccup: the partial file stays so the next try can continue it. */
    Retry,
    Failed,
    /** The user cancelled it (its row is gone). */
    Removed
}

/** Fetches one episode's audio into [dir], continuing a partial `.part` file when there is one. */
class EpisodeDownloader(
    private val downloadDao: EpisodeDownloadDao,
    private val episodeDao: EpisodeDao,
    private val dir: File,
    private val client: OkHttpClient
) {

    suspend fun download(episodeId: Long): DownloadOutcome = withContext(Dispatchers.IO) {
        val episode = episodeDao.getById(episodeId) ?: return@withContext DownloadOutcome.Failed
        dir.mkdirs()
        val name = downloadFileName(episodeId, episode.audioUrl)
        val part = File(dir, "$name.part")
        val target = File(dir, name)
        try {
            val existing = if (part.exists()) part.length() else 0L
            val request = Request.Builder().url(episode.audioUrl)
                .apply { rangeHeader(existing)?.let { header("Range", it) } }
                .build()
            client.newCall(request).execute().use { response ->
                val append = when (classifyResponse(response.code)) {
                    ResponseAction.Append -> true
                    ResponseAction.StartOver -> false
                    ResponseAction.DiscardAndRetry -> { part.delete(); return@withContext DownloadOutcome.Retry }
                    ResponseAction.Retry -> return@withContext DownloadOutcome.Retry
                    ResponseAction.Fail -> return@withContext DownloadOutcome.Failed
                }
                val offset = if (append) existing else 0L
                val body = response.body
                val total = totalFromContentRange(response.header("Content-Range"))
                    ?: body.contentLength().takeIf { it >= 0 }?.let { it + offset }
                downloadDao.updateProgress(episodeId, offset, total)

                var written = offset
                var lastReportAt = 0L
                FileOutputStream(part, append).use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                            written += read
                            val now = System.currentTimeMillis()
                            if (now - lastReportAt >= REPORT_INTERVAL_MS) {
                                lastReportAt = now
                                // The row vanishing means the user cancelled.
                                if (downloadDao.get(episodeId) == null) {
                                    part.delete()
                                    return@withContext DownloadOutcome.Removed
                                }
                                downloadDao.updateProgress(episodeId, written, total)
                            }
                        }
                    }
                }
                if (total != null && written < total) return@withContext DownloadOutcome.Retry
            }
            if (downloadDao.get(episodeId) == null) {
                part.delete()
                return@withContext DownloadOutcome.Removed
            }
            target.delete()
            if (!part.renameTo(target)) return@withContext DownloadOutcome.Failed
            downloadDao.markCompleted(episodeId, target.length(), target.absolutePath, System.currentTimeMillis())
            DownloadOutcome.Done
        } catch (e: IOException) {
            // A full disk won't get better by retrying.
            if (dir.usableSpace < MIN_FREE_BYTES) DownloadOutcome.Failed else DownloadOutcome.Retry
        }
    }

    private companion object {
        const val BUFFER_SIZE = 32 * 1024
        const val REPORT_INTERVAL_MS = 500L
        const val MIN_FREE_BYTES = 2L * 1024 * 1024
    }
}
