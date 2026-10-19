package com.berpod.app.data.repository

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.berpod.app.data.local.EpisodeDao
import com.berpod.app.data.local.EpisodeEntity
import com.berpod.app.playback.PlaybackService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import com.berpod.app.data.local.QueueItemWithEpisode
import com.berpod.app.data.local.QueueDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlaybackUiState(
    val currentEpisodeId: Long? = null,
    val title: String = "",
    val podcastTitle: String = "",
    val artworkUrl: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
    val sleepTimer: SleepTimer? = null,
    val sleepRemainingMs: Long? = null,
    /** Whether a queue entry follows the current episode (enables "next"). */
    val hasNext: Boolean = false
)

/** Where to start an episode: its stored position, or 0 when it was (nearly) finished or never started. */
fun resumeStartMs(lastPositionMs: Long, durationSeconds: Int?): Long {
    if (lastPositionMs <= 0L) return 0L
    val durationMs = (durationSeconds ?: return lastPositionMs).toLong() * 1000L
    return if (durationMs > 0 && lastPositionMs >= durationMs * 98 / 100) 0L else lastPositionMs
}

/** Whether the episode played last is brought back (paused, in the mini player) on the next start: it still exists and was not finished. */
fun shouldRestoreEpisode(episode: EpisodeEntity?): Boolean = episode != null && episode.playedAtMs == null

/** Also used by VideoPlaybackRepository, which persists position on the same 5 s cadence. */
fun shouldPersistPosition(lastPersistedAtMs: Long, nowMs: Long, intervalMs: Long = 5_000L): Boolean =
    nowMs - lastPersistedAtMs >= intervalMs

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackRepository(
    context: Context,
    private val episodeDao: EpisodeDao,
    private val queueDao: QueueDao,
    private val tagRepository: TagRepository,
    private val queueFilterRepository: QueueFilterRepository,
    private val downloadRepository: DownloadRepository
) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    private var controller: MediaController? = null
    private var pendingAction: (() -> Unit)? = null
    private var positionPollJob: Job? = null
    private var lastPersistedAtMs = 0L
    private val prefs = appContext.getSharedPreferences("playback", Context.MODE_PRIVATE)
    // Queue order next/previous and auto-advance follow: the same tag filter as the Fronta screen.
    private var latestQueue: List<QueueItemWithEpisode> = emptyList()
    private var lastPreviousPressAtMs: Long? = null
    private var sleepJob: Job? = null
    // The episode that just finished: its stored position stays 0 until it's played again.
    private var endedEpisodeId: Long? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
            restartPositionPolling(isPlaying)
            if (!isPlaying) {
                val episodeId = _uiState.value.currentEpisodeId
                val position = controller?.currentPosition ?: _uiState.value.positionMs
                if (episodeId != null) persistPosition(episodeId, position)
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val previousEpisodeId = _uiState.value.currentEpisodeId
            val previousPosition = _uiState.value.positionMs
            if (previousEpisodeId != null) {
                persistPosition(previousEpisodeId, previousPosition)
            }
            val metadata = mediaItem?.mediaMetadata
            _uiState.update {
                it.copy(
                    currentEpisodeId = mediaItem?.mediaId?.toLongOrNull(),
                    title = metadata?.title?.toString().orEmpty(),
                    podcastTitle = metadata?.artist?.toString().orEmpty(),
                    artworkUrl = metadata?.artworkUri?.toString(),
                    // 0 for a fresh start; the resume position when the item was loaded with one.
                    positionMs = controller?.currentPosition ?: 0L,
                    // The real duration arrives with STATE_READY; until then the feed's, never the previous episode's.
                    durationMs = metadata?.durationMs ?: 0L
                )
            }
            // The ended item's final position was skipped above; from here on positions are saved normally.
            endedEpisodeId = null
            updateHasNext()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                val duration = controller?.duration ?: C.TIME_UNSET
                _uiState.update {
                    it.copy(
                        durationMs = if (duration == C.TIME_UNSET) it.durationMs else duration,
                        // A restored episode is paused and not polled, so its start position is picked up here.
                        positionMs = controller?.currentPosition ?: it.positionMs
                    )
                }
            }
            if (playbackState == Player.STATE_ENDED) onEpisodeEnded()
        }
    }

    init {
        _uiState.update { it.copy(speed = prefs.getFloat("speed", 1f)) }
        val filterPodcastIds = queueFilterRepository.selectedTagId.flatMapLatest { tagId ->
            if (tagId == null) flowOf(null) else tagRepository.observePodcastIdsWithTag(tagId).map { it.toSet() }
        }
        combine(queueDao.observeQueue(), filterPodcastIds, ::filterQueueOrder)
            .onEach { queue ->
                latestQueue = queue
                updateHasNext()
            }
            .launchIn(scope)
        val sessionToken = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(appContext, sessionToken).buildAsync()
        controllerFuture.addListener(
            {
                val connectedController = controllerFuture.get()
                controller = connectedController
                connectedController.addListener(playerListener)
                _uiState.update { it.copy(isPlaying = connectedController.isPlaying) }
                restartPositionPolling(connectedController.isPlaying)
                connectedController.setPlaybackSpeed(_uiState.value.speed)
                val startedSomething = pendingAction != null
                pendingAction?.invoke()
                pendingAction = null
                if (!startedSomething) restoreCurrent(connectedController)
            },
            ContextCompat.getMainExecutor(appContext)
        )
    }

    /** The podcast the loaded episode belongs to, for opening its page from the player. */
    suspend fun currentFeedId(): Long? = _uiState.value.currentEpisodeId?.let { episodeDao.feedIdOf(it) }

    fun play(episode: EpisodeEntity, podcastTitle: String, podcastImageUrl: String?) {
        if (episode.id == endedEpisodeId) endedEpisodeId = null
        prefs.edit().putLong(KEY_LAST_EPISODE_ID, episode.id).apply()
        // Listening again makes it unplayed until it's finished again.
        if (episode.playedAtMs != null) scope.launch { runCatching { episodeDao.setPlayed(episode.id, null) } }
        // Playing from outside the queue puts the episode first, so next/auto-advance continue with the rest.
        scope.launch { runCatching { queueDao.queueAtFront(episode.id) } }
        val action: () -> Unit = {
            controller?.let { c ->
                val mediaItem = buildMediaItem(episode, podcastTitle, podcastImageUrl)
                // Start position goes with the item: a seekTo() issued right after setMediaItem() over the
                // controller was dropped, so resumed episodes restarted from 0.
                c.setMediaItem(mediaItem, resumeStartMs(episode.lastPositionMs, episode.durationSeconds))
                c.prepare()
                c.play()
            }
        }
        if (controller != null) action() else pendingAction = action
    }

    private fun buildMediaItem(episode: EpisodeEntity, podcastTitle: String, podcastImageUrl: String?): MediaItem =
        MediaItem.Builder()
            .setMediaId(episode.id.toString())
            // The offline copy when there is one, the stream otherwise.
            .setUri(downloadRepository.localFileFor(episode.id)?.let { Uri.fromFile(it).toString() } ?: episode.audioUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(episode.title)
                    .setArtist(podcastTitle)
                    .setArtworkUri((episode.imageUrl ?: podcastImageUrl)?.let(Uri::parse))
                    // Known before the player is ready, so a restored (paused) episode can show its progress bar.
                    .setDurationMs(episode.durationSeconds?.takeIf { it > 0 }?.let { it * 1000L })
                    .build()
            )
            .build()

    /**
     * Back in the app: shows what the player already holds (the service can outlive the app), or else puts the
     * episode played last into it, paused where it stopped, so the mini player is there to carry on. The queue is untouched.
     */
    private fun restoreCurrent(c: MediaController) {
        val item = c.currentMediaItem
        if (item != null) {
            val metadata = item.mediaMetadata
            val duration = c.duration
            _uiState.update {
                it.copy(
                    currentEpisodeId = item.mediaId.toLongOrNull(),
                    title = metadata.title?.toString().orEmpty(),
                    podcastTitle = metadata.artist?.toString().orEmpty(),
                    artworkUrl = metadata.artworkUri?.toString(),
                    positionMs = c.currentPosition,
                    durationMs = if (duration == C.TIME_UNSET) 0L else duration,
                    isPlaying = c.isPlaying
                )
            }
            updateHasNext()
            return
        }
        val lastId = prefs.getLong(KEY_LAST_EPISODE_ID, -1L).takeIf { it > 0 } ?: return
        scope.launch {
            val found = runCatching { episodeDao.getWithFeed(lastId) }.getOrNull()
            if (!shouldRestoreEpisode(found?.episode)) return@launch
            // Something may have been started while it was looked up.
            if (c.currentMediaItem != null) return@launch
            val episode = found!!.episode
            val startMs = resumeStartMs(episode.lastPositionMs, episode.durationSeconds)
            c.setMediaItem(buildMediaItem(episode, found.podcastTitle, found.podcastImageUrl), startMs)
            c.prepare()
            // Shown right away; the player's own callbacks may not have reported the start position yet.
            _uiState.update {
                if (it.currentEpisodeId == episode.id) it.copy(positionMs = startMs) else it
            }
        }
    }

    fun togglePlayPause() {
        controller?.let { c -> if (c.isPlaying) c.pause() else c.play() }
    }

    fun seekTo(positionMs: Long) {
        // Replaying the ended episode in place (seek / previous) should save its position again.
        if (_uiState.value.currentEpisodeId == endedEpisodeId) endedEpisodeId = null
        controller?.seekTo(positionMs)
        _uiState.update { it.copy(positionMs = positionMs) }
    }

    fun skipBy(deltaMs: Long) {
        val c = controller ?: return
        seekTo(skipTarget(c.currentPosition, deltaMs, _uiState.value.durationMs))
    }

    fun playNext() {
        val nextId = nextInQueue(latestQueue.map { it.episode.id }, _uiState.value.currentEpisodeId) ?: return
        latestQueue.firstOrNull { it.episode.id == nextId }?.let { play(it.episode, it.podcastTitle, it.podcastImageUrl) }
    }

    fun playPrevious() {
        val now = System.currentTimeMillis()
        val action = previousAction(lastPreviousPressAtMs, now)
        lastPreviousPressAtMs = now
        val previousId = previousInQueue(latestQueue.map { it.episode.id }, _uiState.value.currentEpisodeId)
        val previous = previousId?.let { id -> latestQueue.firstOrNull { it.episode.id == id } }
        if (action == PreviousAction.PlayPrevious && previous != null) {
            lastPreviousPressAtMs = null
            play(previous.episode.copy(lastPositionMs = 0L), previous.podcastTitle, previous.podcastImageUrl)
        } else {
            seekTo(0L)
        }
    }

    fun cycleSpeed() {
        val speed = nextSpeed(_uiState.value.speed)
        prefs.edit().putFloat("speed", speed).apply()
        controller?.setPlaybackSpeed(speed)
        _uiState.update { it.copy(speed = speed) }
    }

    fun setSleepTimer(minutes: Int) {
        val timer = SleepTimer.At(System.currentTimeMillis() + minutes * 60_000L)
        sleepJob?.cancel()
        _uiState.update { it.copy(sleepTimer = timer) }
        sleepJob = scope.launch {
            while (isActive) {
                val remaining = sleepRemainingMs(timer, System.currentTimeMillis())
                _uiState.update { it.copy(sleepRemainingMs = remaining) }
                if (remaining == 0L) break
                delay(1_000)
            }
            fadeOutAndPause()
            clearSleepTimer()
        }
    }

    fun setSleepAtEndOfEpisode() {
        sleepJob?.cancel()
        _uiState.update { it.copy(sleepTimer = SleepTimer.EndOfEpisode, sleepRemainingMs = null) }
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        clearSleepTimer()
    }

    private fun clearSleepTimer() {
        _uiState.update { it.copy(sleepTimer = null, sleepRemainingMs = null) }
    }

    private suspend fun fadeOutAndPause() {
        val c = controller ?: return
        val start = System.currentTimeMillis()
        try {
            while (true) {
                val elapsed = System.currentTimeMillis() - start
                c.volume = fadeVolume(elapsed)
                if (elapsed >= 5_000) break
                delay(100)
            }
            c.pause()
        } finally {
            c.volume = 1f
        }
    }

    private fun onEpisodeEnded() {
        val endedId = _uiState.value.currentEpisodeId ?: return
        endedEpisodeId = endedId
        // Stopped at the end with nothing after it: "next" shouldn't jump back to the start of the queue.
        _uiState.update { it.copy(hasNext = false) }
        val order = latestQueue.map { it.episode.id }
        val next = nextInQueue(order, endedId)?.let { id -> latestQueue.firstOrNull { it.episode.id == id } }
        val entry = latestQueue.firstOrNull { it.episode.id == endedId }
        scope.launch {
            runCatching { episodeDao.setPlayed(endedId, System.currentTimeMillis()) }
            if (entry != null) runCatching { queueDao.removeEntry(entry.queueEntryId) }
        }
        if (_uiState.value.sleepTimer == SleepTimer.EndOfEpisode) {
            clearSleepTimer()
            return
        }
        if (next != null) play(next.episode, next.podcastTitle, next.podcastImageUrl)
    }

    private fun updateHasNext() {
        _uiState.update {
            val stoppedAtEnd = it.currentEpisodeId != null && it.currentEpisodeId == endedEpisodeId
            it.copy(hasNext = !stoppedAtEnd && nextInQueue(latestQueue.map { q -> q.episode.id }, it.currentEpisodeId) != null)
        }
    }

    private fun restartPositionPolling(isPlaying: Boolean) {
        positionPollJob?.cancel()
        if (!isPlaying) return
        positionPollJob = scope.launch {
            while (isActive) {
                controller?.let { c ->
                    _uiState.update { it.copy(positionMs = c.currentPosition) }
                    val episodeId = _uiState.value.currentEpisodeId
                    if (episodeId != null && shouldPersistPosition(lastPersistedAtMs, System.currentTimeMillis())) {
                        persistPosition(episodeId, c.currentPosition)
                    }
                }
                delay(500)
            }
        }
    }

    private fun persistPosition(episodeId: Long, positionMs: Long) {
        if (episodeId == endedEpisodeId) return
        lastPersistedAtMs = System.currentTimeMillis()
        scope.launch { runCatching { episodeDao.updatePosition(episodeId, positionMs) } }
    }

    private companion object {
        const val KEY_LAST_EPISODE_ID = "last_episode_id"
    }
}
