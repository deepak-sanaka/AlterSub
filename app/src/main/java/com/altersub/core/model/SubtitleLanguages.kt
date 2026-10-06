package com.altersub.core.model

/**
 * The subtitle languages the user can pick on the phone, by ISO 639-1 code, with every label the subtitle sources
 * use for each (OpenSubtitles' three-letter codes, including its regional ones such as "pob", and YTS's names).
 */
object SubtitleLanguages {

    data class Language(val code: String, val name: String, val nativeName: String, val sourceLabels: Set<String>)

    const val DEFAULT = "en"

    val ALL: List<Language> = listOf(
        Language("en", "English", "English", setOf("en", "eng", "english")),
        Language("es", "Spanish", "Español", setOf("es", "spa", "spn", "spl", "ea", "spanish")),
        Language("fr", "French", "Français", setOf("fr", "fre", "fra", "french")),
        Language("de", "German", "Deutsch", setOf("de", "ger", "deu", "german")),
        Language("pt", "Portuguese", "Português", setOf("pt", "por", "pob", "pom", "pt-br", "pt-pt", "portuguese", "brazilian-portuguese")),
        Language("it", "Italian", "Italiano", setOf("it", "ita", "italian")),
        Language("nl", "Dutch", "Nederlands", setOf("nl", "dut", "nld", "dutch")),
        Language("ru", "Russian", "Русский", setOf("ru", "rus", "russian")),
        Language("hi", "Hindi", "हिन्दी", setOf("hi", "hin", "hindi")),
        Language("te", "Telugu", "తెలుగు", setOf("te", "tel", "telugu")),
        Language("ar", "Arabic", "العربية", setOf("ar", "ara", "arabic")),
        Language("zh", "Chinese", "中文", setOf("zh", "chi", "zho", "zhs", "zht", "zhe", "ze", "zh-cn", "zh-tw", "chinese")),
        Language("ja", "Japanese", "日本語", setOf("ja", "jpn", "japanese"))
    )

    fun byCode(code: String?): Language? = ALL.firstOrNull { it.code == code?.trim()?.lowercase() }

    /** Whether a source's language label ("eng", "English", "pob") is the language [code]. */
    fun matches(sourceLabel: String, code: String): Boolean =
        byCode(code)?.sourceLabels?.contains(sourceLabel.trim().lowercase()) == true

    /** "eng" → "English"; labels outside the list are shown as the source gives them. */
    fun nameOf(sourceLabel: String): String =
        ALL.firstOrNull { sourceLabel.trim().lowercase() in it.sourceLabels }?.name ?: sourceLabel
}
