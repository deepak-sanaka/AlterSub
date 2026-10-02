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
