package com.altersub.core.model

data class ContentMetadata(
    val title: String,
    val season: Int? = null,
    val episode: Int? = null,
    val year: Int? = null,
    val imdbId: String? = null,
    val tmdbId: Int? = null,
    val sourcePackage: String = ""
) {
    val isEpisode: Boolean get() = season != null && episode != null

    /** Identity used to decide whether two detections refer to the same title/episode. */
    val contentKey: String get() = "${title.lowercase()}|${season ?: ""}|${episode ?: ""}"

    fun getDisplayName(): String {
        return if (isEpisode) {
            "$title S%02dE%02d".format(season, episode)
        } else if (year != null) {
            "$title ($year)"
        } else {
            title
        }
    }
}
