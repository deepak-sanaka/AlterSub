package com.altersub.server

import com.altersub.core.clock.SubtitleClock
import com.altersub.core.model.ContentMetadata
import com.altersub.core.model.SubtitleStyle
import com.altersub.core.model.SubtitleTrack
import com.altersub.core.parser.SubtitleIndex
import com.altersub.core.session.PickMemory
import com.altersub.core.session.SearchState
import com.altersub.core.session.TitleMatch
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Everything the phone web remote can read and do. Implemented by AlterSubApp; a plain interface so the
 * HTTP routes can be tested on the JVM against a fake.
 */
interface RemoteController {
    val clock: SubtitleClock
    val currentContent: StateFlow<ContentMetadata?>
    val availableTracks: StateFlow<List<SubtitleTrack>>
    val activeTrack: StateFlow<SubtitleTrack?>
    val overlayRunning: StateFlow<Boolean>
    val overlayError: StateFlow<String?>
    val subtitleStyle: StateFlow<SubtitleStyle>

    /** The active track's cues, or null when no subtitles are loaded. */
    val subtitleIndex: StateFlow<SubtitleIndex?>

    /** Films the current title could be (to choose when ambiguous, or to correct a wrong guess). */
    val matches: StateFlow<List<TitleMatch>>
    val searchState: StateFlow<SearchState>

    /** A search typed on the phone, optionally ending in a year. */
    fun searchByText(query: String)

    /** The user picked which film they meant from [matches]; false if it isn't one of them. */
    fun chooseMatch(imdbId: String): Boolean
    fun selectTrack(track: SubtitleTrack)
    fun loadDirectSrt(file: File, displayName: String)
    fun updateSubtitleStyle(change: (SubtitleStyle) -> SubtitleStyle)

    /** Remembered subtitle picks, newest first, for one-tap restore on the phone. */
    fun recentPicks(): List<PickMemory.Pick>

    /** Brings back a remembered pick (track + offset) by its content key; false if it is no longer remembered. */
    fun restorePick(contentKey: String): Boolean

    /** The user moved the sync (offset or "Set time"): remember it for the current title. */
    fun onSyncAdjusted()
}
