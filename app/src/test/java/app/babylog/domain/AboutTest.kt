package app.babylog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The About card's one build line, and the constants it prints. */
class AboutTest {

    @Test
    fun `the version line names the version, the build number and the flavour`() {
        assertEquals(
            "Version 1.1.0 (build 27) \u00b7 release build",
            aboutVersionLine("1.1.0", 27, debug = false),
        )
        assertEquals(
            "Version 1.1.0 (build 27) \u00b7 debug build",
            aboutVersionLine("1.1.0", 27, debug = true),
        )
    }

    @Test
    fun `the line is built from its arguments, never from a constant`() {
        // The row a parent quotes in a bug report must be the build they are running. A hardcoded string
        // would keep saying whatever it said on the day it was typed.
        assertTrue(aboutVersionLine("0.9.0", 12, debug = false).contains("0.9.0"))
        assertTrue(aboutVersionLine("0.9.0", 12, debug = false).contains("12"))
    }

    @Test
    fun `the credits name the children this app is for`() {
        assertEquals("Built by joelm3103, for Kai and Levi.", ABOUT_CREDITS)
        assertTrue(SOURCE_REPOSITORY_URL.startsWith("https://github.com/"))
    }
}
