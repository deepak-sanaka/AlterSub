package com.altersub.core.session

import com.altersub.core.model.ContentMetadata
import com.altersub.core.session.TitleMatching.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TitleMatchingTest {

    // Cinemeta's answer for "Under the open sky": the 2025 film (no subtitles) is listed before the 2020 one
    private val sky2025 = TitleMatch("tt32543911", "Under the Open Sky", 2025)
    private val sky2020 = TitleMatch("tt12801374", "Under the Open Sky", 2020)
    private val painted = TitleMatch("tt37547626", "Under the Painted Sky", null)
    private val skyResults = listOf(sky2025, sky2020, painted)

    private fun decide(query: String, candidates: List<TitleMatch>, interactive: Boolean = true): Decision {
        val parsed = ManualQuery.parse(query)
        return TitleMatching.decide(ContentMetadata(title = parsed.title, year = parsed.year), candidates, interactive, parsed.raw)
    }

    @Test
    fun testFilmsSharingANameAreAskedAbout() {
        assertEquals(Decision.Ambiguous(listOf(sky2025, sky2020)), decide("Under the open sky", skyResults))
    }

    @Test
    fun testAYearPicksTheRightOne() {
        assertEquals(Decision.Chosen(sky2020), decide("Under the open sky 2020", skyResults))
        assertEquals(Decision.Chosen(sky2020), decide("under the open sky (2020)", skyResults))
    }

    @Test
    fun testASingleExactNameIsChosenWithoutAsking() {
        val inception = TitleMatch("tt1375666", "Inception", 2010)
        val other = TitleMatch("tt5295894", "Inception: The Cobol Job", 2010)
        assertEquals(Decision.Chosen(inception), decide("inception", listOf(other, inception)))
    }

    @Test
    fun testPartialTitlesAndTyposAreAskedAboutInteractivelyButGuessedOtherwise() {
        assertEquals(Decision.Ambiguous(listOf(sky2020, sky2025)), decide("Under the open", listOf(sky2020, sky2025)))
        // An automatic detection has nobody to ask: best guess, and the phone still lists the alternatives
        assertEquals(Decision.Chosen(sky2020), decide("Under the open", listOf(sky2020, sky2025), interactive = false))
        assertEquals(Decision.Chosen(sky2025), decide("Under the open sky", skyResults, interactive = false))
    }

    @Test
    fun testATitleEndingInANumberIsNotMistakenForAYear() {
        val ww84 = TitleMatch("tt7126948", "Wonder Woman 1984", 2020)
        val ww = TitleMatch("tt0451279", "Wonder Woman", 2017)
        assertEquals(Decision.Chosen(ww84), decide("Wonder Woman 1984", listOf(ww, ww84)))
        assertEquals(Decision.Chosen(ww), decide("Wonder Woman 2017", listOf(ww, ww84)))
    }

    @Test
    fun testOptionsOfferSameNamedFilmsOrTheTopResults() {
        val many = skyResults + List(10) { TitleMatch("tt9$it", "Sky $it", 2000 + it) }
        assertEquals(listOf(sky2025, sky2020), TitleMatching.options(ContentMetadata(title = "under the open sky"), many))
        assertEquals(TitleMatching.MAX_OPTIONS, TitleMatching.options(ContentMetadata(title = "sky"), many).size)
    }

    @Test
    fun testNoCandidates() {
        assertEquals(Decision.NoMatch, decide("Under the open sky", emptyList()))
    }

    @Test
    fun testManualQueryParsing() {
        assertEquals(ManualQuery("Under the open sky", 2020, "Under the open sky 2020"), ManualQuery.parse("  Under the open sky   2020 "))
        assertEquals(ManualQuery("Inception", 2010, "Inception (2010)"), ManualQuery.parse("Inception (2010)"))
        assertEquals(ManualQuery("Inception", 2010, "Inception, 2010"), ManualQuery.parse("Inception, 2010"))
        // Not years: a bare number is the title, and so is one in the future
        assertEquals(ManualQuery("2012", null, "2012"), ManualQuery.parse("2012"))
        assertEquals(ManualQuery("Blade Runner 2049", null, "Blade Runner 2049"), ManualQuery.parse("Blade Runner 2049"))
        assertNull(ManualQuery.parse("Dark").year)
    }

    @Test
    fun testNormalizationIgnoresCaseAndPunctuation() {
        assertEquals(TitleMatching.normalize("Mission: Impossible – Fallout"), TitleMatching.normalize("mission impossible fallout"))
        assertEquals(TitleMatching.normalize("Fast & Furious"), TitleMatching.normalize("fast and furious"))
    }
}
