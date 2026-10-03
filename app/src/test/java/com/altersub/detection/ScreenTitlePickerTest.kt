package com.altersub.detection

import com.altersub.core.model.ContentMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTitlePickerTest {

    private val app = "com.amazon.amazonvideo.livingroom"

    private fun pick(vararg texts: ScreenText) = ScreenTitlePicker.pick(texts.toList(), app)?.getDisplayName()

    private fun button(text: String) = ScreenText(text, className = "android.widget.Button", clickable = true, heightPx = 40)

    private fun card(text: String) = ScreenText(text, viewId = "title", inRow = true, clickable = true, heightPx = 30)

    @Test
    fun testTheLauncherMenuThatWasTakenForAFilmOnARealTvIsNotATitle() {
        // What the accessibility service read from the TV launcher's long-press menu
        val menu = listOf("Context Menu", "Open", "Move", "Add to favourites", "Info", "Uninstall")
            .map { ScreenText(it, heightPx = 40) }
        assertNull(ScreenTitlePicker.pick(menu, "com.google.android.tvlauncher"))
    }

    @Test
    fun testPlayerOverlayTitleIsPicked() {
        assertEquals(
            "The Grand Budapest Hotel",
            pick(
                ScreenText("The Grand Budapest Hotel", viewId = "player_title", heightPx = 54),
                ScreenText("1:02:03", viewId = "elapsed", heightPx = 30),
                ScreenText("-38:12", viewId = "remaining", heightPx = 30),
                button("Audio & Subtitles"),
                button("Next Episode")
            )
        )
    }

    @Test
    fun testDetailsPageTitleBeatsTheRowOfRelatedCards() {
        // Leanback's details overview: title, metadata line, synopsis, actions, then a row of related titles
        assertEquals(
            "Dark",
            pick(
                ScreenText("Dark", viewId = "lb_details_description_title", heightPx = 70),
                ScreenText("2017 • 3 Seasons • U/A 16+", viewId = "lb_details_description_subtitle", heightPx = 30),
                ScreenText(
                    "A missing child sets four families on a frantic hunt for answers as they unearth a mind-bending mystery.",
                    viewId = "lb_details_description_body", heightPx = 90
                ),
                button("Play"),
                button("More Like This"),
                card("1899"),
                card("Stranger Things"),
                card("The OA")
            )
        )
    }

    @Test
    fun testABrowseScreenOfCardsHasNoTitle() {
        assertNull(
            pick(
                ScreenText("Trending Now", viewId = "row_header", heightPx = 40),
                card("Stranger Things"),
                card("Wednesday"),
                card("The Crown"),
                ScreenText("Top 10 in India Today", viewId = "row_header", heightPx = 40),
                card("Inception")
            )
        )
    }

    @Test
    fun testCardsAreRecognisedByTheirSharedIdEvenOutsideAKnownRow() {
        val cards = listOf("Stranger Things", "Wednesday", "The Crown").map { ScreenText(it, viewId = "item_title", heightPx = 30) }
        assertNull(ScreenTitlePicker.pick(cards, app))
    }

    @Test
    fun testTwoEquallyLikelyTextsAreNotGuessedBetween() {
        assertNull(pick(ScreenText("Inception", heightPx = 40), ScreenText("Interstellar", heightPx = 40)))
    }

    @Test
    fun testALoneTextIsTaken() {
        assertEquals("Inception", pick(ScreenText("Inception", heightPx = 40), button("Resume")))
    }

    @Test
    fun testTheEpisodeLineOutranksTheEpisodeName() {
        assertEquals(
            "Stranger Things S04E01",
            pick(
                ScreenText("Stranger Things S04E01", heightPx = 40),
                ScreenText("Chapter One: The Hellfire Club", heightPx = 40)
            )
        )
    }

    @Test
    fun testTheLargestHeadingWins() {
        assertEquals(
            "Oppenheimer",
            pick(
                ScreenText("Oppenheimer", heading = true, heightPx = 72),
                ScreenText("Cillian Murphy", heightPx = 30)
            )
        )
    }

    @Test
    fun testPageHeadersAreNotTitles() {
        // Leanback's title bar on the emulator's sample app: the page name won when it was the only text
        assertNull(pick(ScreenText("Vertical Video Grid", viewId = "title_text", inHeader = true, heightPx = 120)))
        assertTrue(ScreenTextRules.isHeaderContainer("browse_title_group"))
        assertTrue(ScreenTextRules.isHeaderContainer("toolbar"))
        assertTrue(ScreenTextRules.isHeaderContainer("nav_menu"))
        assertFalse(ScreenTextRules.isHeaderContainer("details_overview"))
        assertFalse(ScreenTextRules.isHeaderContainer("title"))
        assertFalse(ScreenTextRules.isHeaderContainer(null))
    }

    @Test
    fun testLeanbackGuidedStepsAreNotTitles() {
        // The emulator's Leanback sample: a guided step's heading is the wizard's, not a film's
        assertNull(
            pick(
                ScreenText("Guided Steps: 1", viewId = "guidance_breadcrumb", heightPx = 49),
                ScreenText("Guided Step First Page", viewId = "guidance_title", heightPx = 194),
                ScreenText("First step of guided sequence", viewId = "guidance_description", heightPx = 38),
                ScreenText("Continue", viewId = "guidedactions_item_title", heightPx = 49),
                ScreenText("Let's do it", viewId = "guidedactions_item_description", heightPx = 33)
            )
        )
    }

    @Test
    fun testPromptsAndTheirButtonsAreNotTitles() {
        assertNull(pick(ScreenText("Are you still watching?", heightPx = 50), button("Continue Watching"), button("Back")))
    }

    @Test
    fun testTheSameTitleInTwoViewsIsOneCandidate() {
        assertEquals(
            "Inception",
            pick(
                ScreenText("Inception", viewId = "title", heightPx = 60),
                ScreenText("Inception", fromDescription = true, clickable = true, heightPx = 300)
            )
        )
    }

    @Test
    fun testUiText() {
        listOf(
            "Play", "Resume", "1h 45m", "45 min left", "12:34", "-1:02:03 / 1:45:00", "U/A 13+", "PG-13", "TV-MA",
            "98% Match", "3 Seasons", "10 Episodes", "Season 2", "Episode 3", "2023 • 2h 10m • U/A 13+",
            "Top 10 in India Today", "Because you watched Dark", "Continue Watching for Sam", "5.1", "2024",
            "Are you still watching?", "Netflix",
            "A brilliant thief who steals secrets from dreams is offered a chance to have his record erased."
        ).forEach { assertTrue(it, ScreenTextRules.isUiText(it)) }

        listOf(
            "It", "Up", "Mr. Bean", "Coming to America", "Se7en", "Mamma Mia!", "Season of the Witch", "12 Angry Men",
            "The Lord of the Rings: The Fellowship of the Ring", "Everything Everywhere All at Once"
        ).forEach { assertFalse(it, ScreenTextRules.isUiText(it)) }
    }

    @Test
    fun testViewIds() {
        assertEquals(listOf("video", "title"), ScreenTextRules.idWords("videoTitle"))
        assertEquals(listOf("lb", "details", "description", "title"), ScreenTextRules.idWords("lb_details_description_title"))
        assertTrue(ScreenTextRules.isTitleId("show_name"))

        fun notTitle(id: String) = ScreenTextRules.isNotTitle(ScreenText("Inception", viewId = id))
        assertFalse(notTitle("lb_details_description_title"))
        assertFalse(notTitle("arrow_title")) // words, not substrings: "arrow" isn't "row"
        assertTrue(notTitle("toolbar_title"))
        assertTrue(notTitle("alertTitle"))
        assertTrue(notTitle("lb_details_description_subtitle"))
        assertTrue(notTitle("video_duration"))
        assertTrue(ScreenTextRules.isNotTitle(ScreenText("Inception", className = "android.widget.EditText")))
    }

    @Test
    fun testATitleCountsOnlyOnceTwoScansAgree() {
        val confirmation = TitleConfirmation()
        val dark = ContentMetadata(title = "Dark")
        val crown = ContentMetadata(title = "The Crown")

        assertNull(confirmation.offer(dark))
        assertEquals(dark, confirmation.offer(dark))
        assertEquals(dark, confirmation.offer(dark))

        // A card passing by for one scan doesn't count, and an empty scan breaks the streak
        assertNull(confirmation.offer(crown))
        assertNull(confirmation.offer(dark))
        assertNull(confirmation.offer(null))
        assertNull(confirmation.offer(dark))
        assertEquals(dark, confirmation.offer(dark))
    }
}
