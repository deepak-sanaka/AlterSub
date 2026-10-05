package com.altersub.core.session

import com.altersub.core.model.ContentMetadata
import com.altersub.core.model.SubtitleTrack

/** Catalog details shown with a title's subtitle files, so the right film is easy to recognise. */
data class TitleDetails(
    val country: String? = null,
    val runtimeMinutes: Int? = null,
    val year: Int? = null
)

/** One film or series a search found, with its subtitle files in the chosen language. */
data class TitleGroup(
    val content: ContentMetadata,
    val details: TitleDetails?,
    val tracks: List<SubtitleTrack>,
    /** The file last used for this title, if it's among [tracks]. */
    val lastUsedTrackId: String? = null
)

/**
 * What the last search found, grouped by title, for the phone to choose a file from. [manual] is true for a
 * search typed on the phone, whose results wait for the user's pick instead of loading the first file.
 */
data class SearchResults(
    val query: String,
    val language: String,
    val groups: List<TitleGroup>,
    val manual: Boolean
) {
    companion object {
        val NONE = SearchResults(query = "", language = "", groups = emptyList(), manual = false)
    }
}
