package dev.uint.qrserv.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AppLanguageTest {

    @Test
    fun aLanguageIsNamedInItselfAndCapitalized() {
        assertEquals("Deutsch", languageName(Locale.forLanguageTag("de")))
        assertEquals("Español", languageName(Locale.forLanguageTag("es")))
        assertEquals("Français", languageName(Locale.forLanguageTag("fr")))
        assertEquals("Русский", languageName(Locale.forLanguageTag("ru")))
    }

    @Test
    fun scriptsWithoutCaseAreLeftAlone() {
        assertEquals("עברית", languageName(Locale.forLanguageTag("he")))
    }
}
