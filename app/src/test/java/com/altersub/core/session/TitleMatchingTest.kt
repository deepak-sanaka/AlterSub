package com.altersub.core.session

import com.altersub.core.model.ContentMetadata
import com.altersub.core.session.TitleMatching.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun testScreenTitlesMustNameAFilmExactly() {
        assertEquals(sky2025, TitleMatching.verify(ContentMetadata(title = "UNDER THE OPEN SKY"), skyResults))
        assertEquals(sky2020, TitleMatching.verify(ContentMetadata(title = "Under the Open Sky", year = 2020), skyResults))
        assertNull(TitleMatching.verify(ContentMetadata(title = "Under the"), skyResults))
        assertNull(TitleMatching.verify(ContentMetadata(title = "Vertical Video Grid"), listOf(painted)))
        assertNull(TitleMatching.verify(ContentMetadata(title = "Inception"), emptyList()))
    }

    // Cinemeta's answer for "dune" (2026): the 2021 film is now "Dune: Part One"
    private val partOne = TitleMatch("tt1160419", "Dune: Part One", 2021)
    private val partThree = TitleMatch("tt31378509", "Dune: Part Three", 2026)
    private val partTwo = TitleMatch("tt15239678", "Dune: Part Two", 2024)
    private val dune1984 = TitleMatch("tt0087182", "Dune", 1984)
    private val duneResults = listOf(
        partOne, partThree, partTwo, dune1984,
        TitleMatch("tt1935156", "Jodorowsky's Dune", 2013),
        TitleMatch("tt15331462", "Planet Dune", 2021),
        TitleMatch("tt11835714", "Dune Drifter", 2020)
    )

    private fun searched(query: String, candidates: List<TitleMatch>): List<TitleMatch> {
        val parsed = ManualQuery.parse(query)
        return TitleMatching.searchTitles(ContentMetadata(title = parsed.title, year = parsed.year), candidates, parsed.raw)
    }

    @Test
    fun testATypedTitleListsTheFilmAndItsParts() {
        // Catalog order (most popular first); look-alikes such as "Dune Drifter" aren't parts
        assertEquals(listOf(partOne, partThree, partTwo, dune1984), searched("dune", duneResults))
        assertEquals(listOf(sky2025, sky2020), searched("Under the open sky", skyResults))
    }

    @Test
    fun testAYearFindsTheRightPart() {
        assertEquals(listOf(partOne), searched("Dune 2021", duneResults))
        assertEquals(listOf(dune1984), searched("dune (1984)", duneResults))
        assertEquals(listOf(sky2020), searched("Under the open sky 2020", skyResults))
    }

    @Test
    fun testATitleTypedExactlyNamesOneFilm() {
        val ww84 = TitleMatch("tt7126948", "Wonder Woman 1984", 2020)
        val ww = TitleMatch("tt0451279", "Wonder Woman", 2017)
        assertEquals(listOf(ww84), searched("Wonder Woman 1984", listOf(ww, ww84)))
        assertEquals(listOf(partTwo), searched("Dune: Part Two", duneResults))
    }

    @Test
    fun testWithoutAnExactNameTheClosestMatchesAreListed() {
        val results = searched("Under the", skyResults)
        assertEquals(listOf(sky2025, sky2020, painted), results)
        assertTrue(searched("nothing", emptyList()).isEmpty())
    }

    @Test
    fun testParts() {
        assertTrue(TitleMatching.isPartOf("Dune: Part One", "dune"))
        assertTrue(TitleMatching.isPartOf("Kill Bill: Vol. 1", "Kill  Bill"))
        assertTrue(TitleMatching.isPartOf("Spider-Man - No Way Home", "spider-man"))
        assertFalse(TitleMatching.isPartOf("Dune Drifter", "Dune"))
        assertFalse(TitleMatching.isPartOf("Jodorowsky's Dune", "Dune"))
        assertFalse(TitleMatching.isPartOf("Dune", "Dune"))
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
