package com.altersub.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleLanguagesTest {

    @Test
    fun testTenLanguagesWithEnglishFirstAndByDefault() {
        assertEquals(10, SubtitleLanguages.ALL.size)
        assertEquals("en", SubtitleLanguages.ALL.first().code)
        assertEquals("en", SubtitleLanguages.DEFAULT)
        assertEquals(SubtitleLanguages.ALL.size, SubtitleLanguages.ALL.map { it.code }.toSet().size)
    }

    @Test
    fun testSourceLabelsMatchTheirLanguage() {
        assertTrue(SubtitleLanguages.matches("eng", "en"))
        assertTrue(SubtitleLanguages.matches("English", "en"))
        assertTrue(SubtitleLanguages.matches("pob", "pt")) // OpenSubtitles' Brazilian Portuguese
        assertTrue(SubtitleLanguages.matches("fre", "fr"))
        assertTrue(SubtitleLanguages.matches("zht", "zh"))
        assertFalse(SubtitleLanguages.matches("spa", "en"))
        assertFalse(SubtitleLanguages.matches("eng", "xx"))
    }

    @Test
    fun testNames() {
        assertEquals("English", SubtitleLanguages.nameOf("eng"))
        assertEquals("Portuguese", SubtitleLanguages.nameOf("pob"))
        assertEquals("tur", SubtitleLanguages.nameOf("tur")) // Not in the list: shown as given
        assertEquals("Hindi", SubtitleLanguages.byCode("HI")?.name)
        assertNull(SubtitleLanguages.byCode(null))
    }
}
