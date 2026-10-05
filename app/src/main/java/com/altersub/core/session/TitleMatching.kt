package com.altersub.core.session

import com.altersub.core.model.ContentMetadata
import java.util.Calendar

/** One film or series a title can refer to, from a catalog lookup. */
data class TitleMatch(
    val imdbId: String,
    val name: String,
    val year: Int?,
    val type: String = "movie"
) {
    val displayName: String get() = if (year != null) "$name ($year)" else name
}

/** A search typed on the phone, with an optional year: "Under the Open Sky 2020" or "Inception (2010)". */
data class ManualQuery(val title: String, val year: Int?, val raw: String) {
    companion object {
        private val TRAILING_YEAR = Regex("""^(.*?)[\s,]*\(?((?:19|20)\d{2})\)?\s*$""")

        fun parse(query: String): ManualQuery {
            val raw = query.trim().replace(Regex("\\s+"), " ")
            val match = TRAILING_YEAR.matchEntire(raw)
            val year = match?.groupValues?.get(2)?.toInt()
            val title = match?.groupValues?.get(1)?.trim().orEmpty()
            // A bare year is a title ("2012"), and so is a "year" that hasn't happened yet ("Blade Runner 2049")
            val latestYear = Calendar.getInstance().get(Calendar.YEAR) + 1
            return if (year != null && title.isNotEmpty() && year <= latestYear) ManualQuery(title, year, raw) else ManualQuery(raw, null, raw)
        }
    }
}

/**
 * Picks the film a title refers to among catalog matches, or decides the user has to choose. Several films can
 * share a name (remakes, or two different "Under the Open Sky"), and catalogs don't list the wanted one first,
 * so taking the first hit loaded nothing, or the wrong film's subtitles.
 */
object TitleMatching {

    sealed interface Decision {
        data class Chosen(val match: TitleMatch) : Decision
        /** Several plausible films: the phone should ask. */
        data class Ambiguous(val options: List<TitleMatch>) : Decision
        object NoMatch : Decision
    }

    const val MAX_OPTIONS = 6

    /** Most films a typed search lists at once. */
    const val MAX_SEARCHED = 4

    private val WHITESPACE = Regex("\\s+")
    private const val PART_SEPARATORS = ":-–—"

    /**
     * [interactive] is true for searches typed on the phone, where the user is there to answer; automatic
     * detections take the best guess instead (and the phone still offers the other matches).
     * [rawQuery] is the text as typed, so a title ending in a number ("Wonder Woman 1984") isn't mistaken
     * for a title plus a year.
     */
    fun decide(metadata: ContentMetadata, candidates: List<TitleMatch>, interactive: Boolean, rawQuery: String? = null): Decision {
        if (candidates.isEmpty()) return Decision.NoMatch

        if (rawQuery != null && normalize(rawQuery) != normalize(metadata.title)) {
            candidates.singleOrNull { normalize(it.name) == normalize(rawQuery) }?.let { return Decision.Chosen(it) }
        }

        val sameName = candidates.filter { normalize(it.name) == normalize(metadata.title) }
        metadata.year?.let { year ->
            // The film from that year: one with the name, else one of its parts ("Dune 2021" is "Dune: Part One"),
            // else any match. Several from that year leave the choice to the rules below.
            val parts = candidates.filter { isPartOf(it.name, metadata.title) }
            for (pool in listOf(sameName, parts, candidates)) {
                val sameYear = pool.filter { it.year == year }
                if (sameYear.size == 1) return Decision.Chosen(sameYear.single())
                if (sameYear.size > 1) break
            }
        }

        return when {
            sameName.size == 1 -> Decision.Chosen(sameName.single())
            sameName.size > 1 -> if (interactive) Decision.Ambiguous(sameName.take(MAX_OPTIONS)) else Decision.Chosen(sameName.first())
            // Nothing by that exact name: a partial title or a typo. Ask, rather than guess, when someone is there
            interactive -> Decision.Ambiguous(candidates.take(MAX_OPTIONS))
            else -> Decision.Chosen(candidates.first())
        }
    }

    /**
     * The films a search typed on the phone lists, in the catalog's order (its most popular first). A year, or a
     * title typed exactly as the catalog has it, names one film. Otherwise every film sharing the name is listed,
     * with its parts: IMDb renamed the 2021 "Dune" to "Dune: Part One", so a search for "Dune" found only 1984's.
     * With no exact name (a partial title or a typo), the closest matches.
     */
    fun searchTitles(metadata: ContentMetadata, candidates: List<TitleMatch>, rawQuery: String?): List<TitleMatch> {
        val decision = decide(metadata, candidates, interactive = true, rawQuery)
        if (decision is Decision.NoMatch) return emptyList()
        if (decision is Decision.Chosen) {
            val typedExactly = rawQuery != null && normalize(rawQuery) != normalize(metadata.title) &&
                normalize(decision.match.name) == normalize(rawQuery)
            if (metadata.year != null || typedExactly) return listOf(decision.match)
        }
        val related = candidates.filter { normalize(it.name) == normalize(metadata.title) || isPartOf(it.name, metadata.title) }
        val fallback = (decision as? Decision.Ambiguous)?.options ?: listOf((decision as Decision.Chosen).match)
        return related.ifEmpty { fallback }.take(MAX_SEARCHED)
    }

    /** "Dune: Part One" is a part of "Dune": the same title, then a colon or a dash and more. */
    fun isPartOf(name: String, title: String): Boolean {
        val full = name.trim().lowercase()
        val base = title.trim().lowercase().replace(WHITESPACE, " ")
        if (base.isEmpty() || !full.startsWith(base)) return false
        val rest = full.substring(base.length).trimStart()
        return rest.isNotEmpty() && rest[0] in PART_SEPARATORS
    }

    /**
     * For a title read off the screen: the film or series with exactly that name (the year, when known, settles
     * which), or null. Screen text the catalog doesn't know by name is far more likely a page header or a menu
     * than an obscure film, so it is never searched.
     */
    fun verify(metadata: ContentMetadata, candidates: List<TitleMatch>): TitleMatch? {
        val sameName = candidates.filter { normalize(it.name) == normalize(metadata.title) }
        return metadata.year?.let { year -> sameName.firstOrNull { it.year == year } } ?: sameName.firstOrNull()
    }

    /** "Under the Open Sky" == "under the open sky"; punctuation and "&"/"and" differences don't matter. */
    fun normalize(title: String): String = title.lowercase()
        .replace("&", " and ")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
}
