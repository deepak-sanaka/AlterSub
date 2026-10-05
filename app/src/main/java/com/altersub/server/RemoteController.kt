package com.altersub.server

import com.altersub.core.clock.SubtitleClock
import com.altersub.core.model.ContentMetadata
import com.altersub.core.model.SubtitleStyle
import com.altersub.core.model.SubtitleTrack
import com.altersub.core.parser.SubtitleIndex
import com.altersub.core.session.PickMemory
import com.altersub.core.session.SearchResults
import com.altersub.core.session.SearchState
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Everything the phone web remote can read and do. Implemented by AlterSubApp; a plain interface so the
 * HTTP routes can be tested on the JVM against a fake.
 */
interface RemoteController {
    val clock: SubtitleClock
    val currentContent: StateFlow<ContentMetadata?>
    val activeTrack: StateFlow<SubtitleTrack?>
    val overlayRunning: StateFlow<Boolean>
    val overlayError: StateFlow<String?>
    val subtitleStyle: StateFlow<SubtitleStyle>

    /** The active track's cues, or null when no subtitles are loaded. */
    val subtitleIndex: StateFlow<SubtitleIndex?>

    val searchState: StateFlow<SearchState>

    /** What the latest search found, grouped by title, for the phone to pick a file from. */
    val searchResults: StateFlow<SearchResults>

    /** How long each listed file runs (ms), by track id; -1 when it couldn't be read. Missing: not checked yet. */
    val subtitleDurations: StateFlow<Map<String, Long>>

    /** The subtitle language every search asks for (ISO 639-1, one of SubtitleLanguages). */
    val subtitleLanguage: StateFlow<String>

    /** Changes [subtitleLanguage], keeps it, and searches the shown results again in it. False if unknown. */
    fun setSubtitleLanguage(code: String): Boolean

    /** A search typed on the phone, optionally ending in a year. */
    fun searchByText(query: String)

    /** Shows a file from [searchResults] (its title becomes what's playing); false if it isn't listed. */
    fun useSearchResult(trackId: String): Boolean

    /** The phone is showing [searchResults]: read each file's length into [subtitleDurations]. */
    fun onSearchResultsViewed()

    fun loadDirectSrt(file: File, displayName: String)
    fun updateSubtitleStyle(change: (SubtitleStyle) -> SubtitleStyle)

    /** Remembered subtitle picks, newest first, for one-tap restore on the phone. */
    fun recentPicks(): List<PickMemory.Pick>

    /** Brings back a remembered pick (track + offset) by its content key; false if it is no longer remembered. */
    fun restorePick(contentKey: String): Boolean

    /** The user moved the sync (offset or "Set time"): remember it for the current title. */
    fun onSyncAdjusted()
}
