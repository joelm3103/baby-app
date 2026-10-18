package app.babylog.data.medications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The SHIPPED medication asset, not a fixture.
 *
 * The list is the one thing in Phase 11 that carries a safety claim, so it is tested rather than trusted:
 * it must parse from the real APK bytes, and it must contain no doses. A single stray "2.5 ml" in an
 * asset whose whole purpose is to appear as a suggestion would turn a name picker into advice.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MedicationCatalogueTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun shipped(): String = context.assets
        .open(MedicationCatalogueParser.ASSET_PATH)
        .bufferedReader()
        .use { it.readText() }

    @Test
    fun `the shipped list parses from the real asset`() {
        val names = MedicationCatalogueParser.parse(shipped())

        assertTrue("expected a usable list, got ${names.size} names", names.size >= 10)
        assertTrue(names.contains("Paracetamol (Calpol)"))
        // Order is curated (most common first), so the parser must not sort or shuffle it.
        assertEquals("Paracetamol (Calpol)", names.first())
    }

    @Test
    fun `no entry in the shipped list contains a dose`() {
        // Numbers are what a dose looks like. None of these names has one, and none ever should.
        val withNumbers = MedicationCatalogueParser.parse(shipped()).filter { name -> name.any(Char::isDigit) }

        assertEquals(emptyList<String>(), withNumbers)
    }

    @Test
    fun `the parser skips blanks and de-duplicates`() {
        val names = MedicationCatalogueParser.parse(
            """{"medications":["Calpol","","   ","Calpol","Vitamin D drops"]}""",
        )

        assertEquals(listOf("Calpol", "Vitamin D drops"), names)
    }
}
