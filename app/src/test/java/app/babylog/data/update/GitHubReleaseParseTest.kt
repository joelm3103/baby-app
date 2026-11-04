package app.babylog.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The one part of the check that touches `org.json`, so it needs the Android runtime — hence Robolectric and a
 * class of its own, which keeps [UpdateCheckTest] plain and quick.
 *
 * Both fields are required: a tag with no page is a version the parent cannot fetch, and a page with no tag is
 * a link that says nothing about whether it is newer.
 */
@RunWith(RobolectricTestRunner::class)
// Pinned like every other Robolectric class here: the unpinned default SDK fails on this machine's JDK with
// "Failed to interact with raw FileDescriptor internals" before a single test body runs.
@Config(sdk = [34])
class GitHubReleaseParseTest {

    @Test
    fun aReleaseIsATagAndAPage() {
        assertEquals(
            ReleaseInfo("v1.5.0", "https://github.com/joelm3103/baby-app/releases/tag/v1.5.0"),
            parseRelease(
                """{"tag_name":"v1.5.0","html_url":"https://github.com/joelm3103/baby-app/releases/tag/v1.5.0"}""",
            ),
        )
    }

    @Test
    fun halfAReleaseIsNoRelease() {
        assertNull(parseRelease("""{"tag_name":"v1.5.0"}"""))
        assertNull(parseRelease("""{"html_url":"https://example"}"""))
        assertNull(parseRelease("""{"tag_name":"","html_url":"https://example"}"""))
        assertNull(parseRelease("not json"))
        assertNull(parseRelease(""))
    }
}
