package com.berpod.app.ui.common

import com.berpod.app.data.local.DownloadStatus
import com.berpod.app.ui.common.EpisodeMenuAction.AddToQueue
import com.berpod.app.ui.common.EpisodeMenuAction.CancelDownload
import com.berpod.app.ui.common.EpisodeMenuAction.DeleteDownload
import com.berpod.app.ui.common.EpisodeMenuAction.Download
import com.berpod.app.ui.common.EpisodeMenuAction.MarkPlayed
import com.berpod.app.ui.common.EpisodeMenuAction.MarkUnplayed
import com.berpod.app.ui.common.EpisodeMenuAction.OpenEpisode
import com.berpod.app.ui.common.EpisodeMenuAction.OpenPodcast
import com.berpod.app.ui.common.EpisodeMenuAction.RemoveFromQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EpisodeMenuTest {

    private fun actions(
        isQueued: Boolean = false,
        isPlayed: Boolean = false,
        download: DownloadStatus? = null,
        canOpenPodcast: Boolean = true,
        inNew: Boolean = false
    ) = episodeMenuActions(isQueued, isPlayed, download, canOpenPodcast, inNew)

    @Test
    fun `a fresh episode can be downloaded, queued, marked played and opened`() {
        assertEquals(listOf(Download, AddToQueue, MarkPlayed, OpenEpisode, OpenPodcast), actions())
    }

    @Test
    fun `a queued episode offers to leave the queue`() {
        assertEquals(RemoveFromQueue, actions(isQueued = true)[1])
    }

    @Test
    fun `a played episode offers to be marked unplayed`() {
        assertEquals(MarkUnplayed, actions(isPlayed = true)[2])
    }

    @Test
    fun `the download entry follows the state of the offline copy`() {
        assertEquals(Download, actions(download = null).first())
        assertEquals(Download, actions(download = DownloadStatus.FAILED).first())
        assertEquals(CancelDownload, actions(download = DownloadStatus.QUEUED).first())
        assertEquals(CancelDownload, actions(download = DownloadStatus.DOWNLOADING).first())
        assertEquals(DeleteDownload, actions(download = DownloadStatus.COMPLETED).first())
    }

    @Test
    fun `the podcast's own page has no entry for its podcast`() {
        assertFalse(OpenPodcast in actions(canOpenPodcast = false))
        assertEquals(OpenEpisode, actions(canOpenPodcast = false).last())
    }

    @Test
    fun `in Nové taking an episode out of the list replaces marking it played`() {
        val inNew = actions(inNew = true)
        assertEquals(EpisodeMenuAction.RemoveFromNew, inNew[2])
        assertFalse(MarkPlayed in inNew)
        assertEquals(listOf(Download, AddToQueue, EpisodeMenuAction.RemoveFromNew, OpenEpisode, OpenPodcast), inNew)
    }
}
