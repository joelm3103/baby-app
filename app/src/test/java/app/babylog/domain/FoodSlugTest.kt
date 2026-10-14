package app.babylog.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** Slug derivation is pure, so it is tested on the JVM with no Android runtime. */
class FoodSlugTest {

    @Test
    fun `lowercases and separates words with a single dash`() {
        assertEquals("greek-yoghurt", foodSlug("Greek Yoghurt"))
        assertEquals("sweet-potato", foodSlug("  sweet   potato "))
    }

    @Test
    fun `drops punctuation instead of encoding it`() {
        assertEquals("peanut-butter-smooth", foodSlug("Peanut Butter (smooth)"))
        assertEquals("fish-pie", foodSlug("Fish & pie!"))
    }

    @Test
    fun `folds accents to ascii so the asset can use human spelling`() {
        assertEquals("puree", foodSlug("purée"))
        assertEquals("creme-fraiche", foodSlug("crème fraîche"))
    }

    @Test
    fun `keeps digits`() {
        assertEquals("stage-2-porridge", foodSlug("Stage 2 porridge"))
    }

    @Test
    fun `a name with no ascii letters or digits yields an empty slug`() {
        // Emoji and non-Latin scripts have no stable ASCII slug; the caller generates an id instead.
        assertEquals("", foodSlug("🍌"))
        assertEquals("", foodSlug("!!! ..."))
    }

    @Test
    fun `uniqueFoodSlug leaves a free base alone`() {
        assertEquals("banana", uniqueFoodSlug("Banana", taken = setOf("apple")))
    }

    @Test
    fun `uniqueFoodSlug disambiguates a collision rather than overwriting`() {
        assertEquals("banana-2", uniqueFoodSlug("Banana", taken = setOf("banana")))
        assertEquals("banana-3", uniqueFoodSlug("banana", taken = setOf("banana", "banana-2")))
    }

    @Test
    fun `uniqueFoodSlug returns empty when there is nothing to slug`() {
        assertEquals("", uniqueFoodSlug("🍌", taken = emptySet()))
    }
}
