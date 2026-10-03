package com.altersub.detection

import com.altersub.core.model.ContentMetadata

/** A piece of text read from a streaming app's screen, with the hints that tell a title apart from the rest of the UI. */
data class ScreenText(
    val text: String,
    /** The view's ID without the package ("title" for "com.app:id/title"), when the app sets one. */
    val viewId: String? = null,
    val className: String? = null,
    val clickable: Boolean = false,
    val heading: Boolean = false,
    /** Inside a horizontal row or a grid of cards: something to browse, not what's playing. */
    val inRow: Boolean = false,
    /** Inside a toolbar, page header, menu or dialog: the app's own furniture ([ScreenTextRules.isHeaderContainer]). */
    val inHeader: Boolean = false,
    /** Read from the content description because the view shows no text. */
    val fromDescription: Boolean = false,
    /** On-screen height in pixels, standing in for the font size. */
    val heightPx: Int = 0
)

/**
 * Picks the title of what's playing from the text on a streaming app's screen, or nothing when no text clearly stands
 * out (KI-5). Nothing is the safe answer: the user can still search from the phone, whereas a wrong title downloads
 * subtitles for the wrong film.
 */
object ScreenTitlePicker {

    data class Candidate(val text: ScreenText, val metadata: ContentMetadata, val score: Int)

    /** The winner needs this score, or [MIN_SCORE_ALONE] when nothing else on screen could be a title... */
    const val MIN_SCORE = 2
    const val MIN_SCORE_ALONE = 1

    /** ...and must beat the best other title by this much. */
    const val MIN_LEAD = 2

    fun pick(texts: List<ScreenText>, packageName: String): ContentMetadata? = decide(rank(texts, packageName))

    /** Every text that could be a title, best first, one per title. */
    fun rank(texts: List<ScreenText>, packageName: String): List<Candidate> {
        // Cards in a list share their view IDs; the title of what's playing has its own
        val repeatedIds = texts.mapNotNull { it.viewId }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        val titles = texts.mapNotNull { text ->
            if (ScreenTextRules.isNotTitle(text)) null
            else TitleSanitizer.sanitize(text.text, packageName)?.let { text to it }
        }
        val tallestPx = titles.maxOfOrNull { (text, _) -> text.heightPx } ?: 0
        return titles
            .map { (text, metadata) -> Candidate(text, metadata, score(text, metadata, tallestPx, text.viewId in repeatedIds)) }
            .sortedByDescending { it.score }
            .distinctBy { it.metadata.contentKey }
    }

    fun decide(ranked: List<Candidate>): ContentMetadata? {
        val best = ranked.firstOrNull() ?: return null
        val runnerUp = ranked.getOrNull(1)
        val clear = if (runnerUp == null) best.score >= MIN_SCORE_ALONE
        else best.score >= MIN_SCORE && best.score - runnerUp.score >= MIN_LEAD
        return best.metadata.takeIf { clear }
    }

    private fun score(text: ScreenText, metadata: ContentMetadata, tallestPx: Int, repeatedId: Boolean): Int {
        var score = 0
        if (!repeatedId && ScreenTextRules.isTitleId(text.viewId)) score += 3
        if (text.heading) score += 2
        if (metadata.isEpisode) score += 2
        if (text.heightPx > 0 && text.heightPx == tallestPx) score += 1
        if (text.inRow || repeatedId) score -= 3
        if (text.clickable) score -= 1
        if (text.fromDescription) score -= 1
        return score
    }
}

/** What a streaming app's screen text looks like when it's UI rather than a title. */
internal object ScreenTextRules {

    private val camelCaseBoundary = Regex("([a-z0-9])([A-Z])")
    private val idSeparator = Regex("[^a-z0-9]+")
    private val whitespace = Regex("\\s+")

    // Durations and clock times ("1h 45m", "45 min left", "12:34", "-1:02:03 / 1:45:00")
    private val durationPattern = Regex(
        "(?i)^(?:\\d+\\s*(?:h|hr|hrs|hours?|m|mins?|minutes?|s|secs?|seconds?)\\b\\s*)+(?:left|remaining)?$"
    )
    private val clockPattern = Regex("^[-–]?\\d{1,2}(?::\\d{2}){1,2}(?:\\s*/\\s*\\d{1,2}(?::\\d{2}){1,2})?$")

    // Age ratings ("U/A 13+", "PG-13", "TV-MA", "18+"), match scores ("98% Match"), counts and bare numbers
    private val ratingPattern = Regex("(?i)^(?:u/?a\\s*\\d{0,2}\\+?|pg(?:-13)?|nc-17|tv-[a-z0-9]+|\\d{1,2}\\+)$")
    private val percentPattern = Regex("^\\d{1,3}\\s*%")
    private val countPattern = Regex("(?i)^\\d+\\s+(?:seasons?|episodes?|parts?|chapters?|videos?|items?|titles?)$")
    private val bareSeasonOrEpisode = Regex("(?i)^(?:season|series|episode|ep\\.?|e|chapter|part|vol\\.?|volume)\\s*\\d+$")
    private val numberPattern = Regex("^[\\d\\s.,:/+-]+$")

    private val uiTexts = hashSetOf(
        // Player and details-page actions
        "play", "pause", "resume", "restart", "start over", "play from beginning", "watch now", "watch", "continue",
        "play trailer", "watch trailer", "trailer", "trailers", "teaser", "episodes", "seasons", "more info", "details",
        "more like this", "more details", "audio", "subtitles", "audio & subtitles", "audio and subtitles",
        "audio & subtitle", "languages", "next episode", "next", "previous", "skip intro", "skip recap",
        "skip credits", "watch credits", "skip ad", "skip ads", "rewind", "fast forward", "forward", "replay",
        "my list", "add to my list", "remove from my list", "watchlist", "add to watchlist", "remove from watchlist",
        "rate", "like", "dislike", "share", "download", "rent", "buy", "subscribe", "upgrade", "cast", "cast & crew",
        "about", "info", "speed", "quality", "settings", "report a problem", "close", "cancel", "back", "ok", "done",
        "yes", "no", "exit", "retry", "loading", "please wait", "advertisement", "sponsored",
        // Navigation and rows
        "home", "search", "browse", "library", "for you", "live", "live tv", "movies", "tv shows", "shows", "series",
        "sports", "kids", "news", "music", "anime", "documentaries", "originals", "categories", "genres", "collections",
        "my stuff", "downloads", "new & popular", "new and popular", "popular", "trending now", "top picks",
        "continue watching", "recently added", "coming soon", "up next", "now playing", "new", "free", "premium",
        "profile", "profiles", "switch profile", "who's watching?", "sign in", "sign out", "log in", "log out",
        "apps", "favourites", "favorites", "context menu", "open", "move", "uninstall", "add to favourites",
        "add to favorites", "customize channels",
        // Badges
        "hd", "sd", "4k", "uhd", "hdr", "hdr10", "hdr10+", "dolby vision", "dolby atmos", "atmos", "5.1", "cc",
        "sdh", "ad", "audio description", "imdb",
        // App names shown as logos or headers
        "netflix", "prime video", "amazon prime video", "youtube", "disney+", "hotstar", "jiohotstar",
        "disney+ hotstar", "sony liv", "sonyliv", "zee5", "plex", "jellyfin", "emby", "kodi", "vlc", "apple tv",
        "apple tv+", "max", "hulu", "crunchyroll", "mubi", "jiotv"
    )

    private val uiPrefixes = listOf(
        "because you ", "top 10", "top ten", "more like ", "continue watching", "trending", "popular on ", "new on ",
        "recommended", "watch again", "recently ", "leaving soon", "new episode", "new season", "available ",
        "starring", "cast:", "director", "directed by", "creators", "audio:", "subtitles:", "rated ",
        "season finale", "watch in ", "only on ", "episodes in ", "added "
    )

    // Views that hold something other than a title: controls, inputs, and progress
    private val notTitleClassSuffixes = listOf("Button", "EditText", "Switch", "CheckBox", "SeekBar", "ProgressBar", "TabView")

    // An ID with any of these words labels something else, even when it also says "title" (toolbar_title, row_header_title)
    private val notTitleIdWords = hashSetOf(
        "toolbar", "header", "row", "section", "category", "tab", "menu", "dialog", "alert", "button", "btn", "card",
        "tile", "poster", "thumbnail", "channel", "profile", "user", "account", "nav", "logo", "badge", "hint",
        "error", "toast", "notification", "settings", "search", "track", "language", "audio",
        // Leanback's guided steps (sign-in, settings and purchase wizards)
        "guidance", "guidedactions", "breadcrumb"
    )

    // ...and so does an ID ending in one of these (lb_details_description_subtitle, video_duration)
    private val notTitleIdEndings = hashSetOf(
        "subtitle", "description", "desc", "body", "summary", "synopsis", "info", "genre", "genres", "rating",
        "duration", "time", "date", "year", "label", "message", "caption", "metadata", "meta", "count", "progress"
    )

    private val titleIdWords = hashSetOf("title", "name")

    private val headerContainerWords = hashSetOf(
        "toolbar", "appbar", "actionbar", "titlebar", "header", "menu", "dialog", "nav", "navigation", "sidebar",
        "drawer", "tabs"
    )

    fun isNotTitle(text: ScreenText): Boolean {
        if (text.inHeader) return true
        val className = text.className.orEmpty()
        if (notTitleClassSuffixes.any { className.endsWith(it) }) return true
        val words = idWords(text.viewId)
        if (words.isNotEmpty() && (words.any { it in notTitleIdWords } || words.last() in notTitleIdEndings)) return true
        return isUiText(text.text)
    }

    fun isTitleId(viewId: String?): Boolean = idWords(viewId).any { it in titleIdWords }

    /**
     * Whether a view with this ID holds page furniture rather than content: a toolbar, header, menu or dialog, or
     * Leanback's title bar ("browse_title_group"), whose text names the page ("Vertical Video Grid"), not a film.
     */
    fun isHeaderContainer(viewId: String?): Boolean {
        val words = idWords(viewId)
        return words.any { it in headerContainerWords } || ("title" in words && ("group" in words || "bar" in words))
    }

    fun isUiText(raw: String): Boolean {
        val text = raw.trim().replace(whitespace, " ")
        if (text.length > MAX_TITLE_LENGTH) return true
        val lower = text.lowercase()
        if (lower in uiTexts || uiPrefixes.any { lower.startsWith(it) }) return true
        if (durationPattern.matches(text) || clockPattern.matches(text) || ratingPattern.matches(text)) return true
        if (percentPattern.containsMatchIn(text) || countPattern.matches(text) || bareSeasonOrEpisode.matches(text)) return true
        if (numberPattern.matches(text)) return true
        // Metadata lines ("2023 • 2h 10m • U/A 13+")
        if ('•' in text || '·' in text) return true
        return isSentence(text)
    }

    /** Synopses and prompts ("Are you still watching?") rather than names. */
    private fun isSentence(text: String): Boolean {
        val words = text.split(' ').size
        if (words >= 3 && text.endsWith('?')) return true
        return words >= 8 && (text.last() in ".!…" || SENTENCE_BREAK.containsMatchIn(text))
    }

    /** "videoTitle", "video_title" and "lb-video-title" all become words: [video, title]. */
    fun idWords(viewId: String?): List<String> {
        if (viewId.isNullOrEmpty()) return emptyList()
        return viewId.replace(camelCaseBoundary, "$1 $2").lowercase().split(idSeparator).filter { it.isNotEmpty() }
    }

    private val SENTENCE_BREAK = Regex("[.!?] [A-Z]")
    private const val MAX_TITLE_LENGTH = 100
}

/**
 * Lets a screen title through only once two scans in a row agree, so a card that passes by or a menu that opens for
 * a moment can't switch the subtitles.
 */
class TitleConfirmation {
    private var lastKey: String? = null

    /** The title once it has been seen twice in a row; null while it's new, or when the scan found none. */
    fun offer(metadata: ContentMetadata?): ContentMetadata? {
        val key = metadata?.contentKey
        val confirmed = key != null && key == lastKey
        lastKey = key
        return metadata.takeIf { confirmed }
    }

    fun reset() {
        lastKey = null
    }
}
