package com.altersub.detection

import com.altersub.core.model.ContentMetadata

object TitleSanitizer {

    private val seasonEpisodePattern = Regex("(?i)(?:s|season\\s*)(\\d{1,2})[\\s._-]*(?:e|ep|episode\\s*)(\\d{1,3})")
    private val standaloneEpisodePattern = Regex("(?i)(?:e|ep|episode)\\s*(\\d{1,3})")
    private val yearPattern = Regex("\\b(19\\d{2}|20\\d{2})\\b")

    // Cleanup patterns are compiled once: sanitize() runs on every accessibility scan
    private val parenYearPattern = Regex("\\(\\s*\\d{4}\\s*\\)")
    private val bracketYearPattern = Regex("\\[\\s*\\d{4}\\s*\\]")
    private val qualityTagPattern = Regex("(?i)[\\(\\[]?(?:4k|hdr|uhd|1080p|720p|h264|h265|bluray|web-?rip)[\\)\\]]?")
    private val emptyBracketsPattern = Regex("[\\(\\[]\\s*[\\)\\]]")
    private val trailingSeparatorPattern = Regex("[-_:|•]+$")

    private val junkKeywords = hashSetOf(
        "play", "pause", "resume", "episodes", "more info", "audio & subtitles",
        "search", "settings", "next episode", "skip intro", "home", "back"
    )

    /**
     * Sanitizes raw screen or media session text into structured ContentMetadata.
     */
    fun sanitize(rawText: String, packageName: String = ""): ContentMetadata? {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty() || isUiJunk(trimmed)) return null

        var season: Int? = null
        var episode: Int? = null
        var year: Int? = null

        // 1. Detect SxxExx or Season X Episode Y
        val seMatch = seasonEpisodePattern.find(trimmed)
        if (seMatch != null) {
            season = seMatch.groupValues[1].toIntOrNull()
            episode = seMatch.groupValues[2].toIntOrNull()
        } else {
            val epMatch = standaloneEpisodePattern.find(trimmed)
            if (epMatch != null) {
                season = 1
                episode = epMatch.groupValues[1].toIntOrNull()
            }
        }

        // 2. Detect Year
        val yearMatch = yearPattern.find(trimmed)
        if (yearMatch != null) {
            year = yearMatch.groupValues[1].toIntOrNull()
        }

        // 3. Clean Show/Movie Name
        var cleanTitle = trimmed
        if (seMatch != null) {
            cleanTitle = cleanTitle.substring(0, seMatch.range.first).trim()
        }
        cleanTitle = cleanTitle
            .replace(parenYearPattern, "")
            .replace(bracketYearPattern, "")
            .replace(yearPattern, "")
            .replace(qualityTagPattern, "")
            .replace(emptyBracketsPattern, "")
            .replace(trailingSeparatorPattern, "")
            .trim()

        if (cleanTitle.length < 2) return null

        return ContentMetadata(
            title = cleanTitle,
            season = season,
            episode = episode,
            year = year,
            sourcePackage = packageName
        )
    }

    private fun isUiJunk(text: String): Boolean {
        val lower = text.lowercase()
        return junkKeywords.contains(lower) || lower.length > 120
    }
}
