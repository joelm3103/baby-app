package app.babylog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The food-catalogue rule, as a pure function: **the published catalogue beats the bundled asset, a
 * pending entry fills a gap, and this phone's own rename wins for display**.
 *
 * Each case is a way the three sources can disagree about one id, and each wrong answer has a cost: a
 * rename that never travels, a parent's food becoming "custom" again after it was published, or a row
 * invented for an id nothing knows.
 */
class FoodCatalogueMergeTest {

    private fun claim(name: String, category: String = "Other") = CatalogueClaim(name, category)

    @Test
    fun `the bundled asset alone resolves a food`() {
        val food = resolveFood("banana", seeded = claim("Banana", "Fruit"))!!

        assertEquals("Banana", food.name)
        assertEquals("Fruit", food.category)
        assertTrue("a seeded food is never the parent's addition", !food.custom)
    }

    @Test
    fun `a published rename beats the asset and both phones see it without an APK`() {
        val food = resolveFood(
            "courgette",
            seeded = claim("Courgette", "Vegetable"),
            published = claim("Zucchini", "Vegetable"),
        )!!

        assertEquals("Zucchini", food.name)
    }

    @Test
    fun `a pending food is custom while no catalogue carries it`() {
        val food = resolveFood("callaloo", pending = claim("Callaloo", "Custom"))!!

        assertEquals("Callaloo", food.name)
        assertTrue(food.custom)
    }

    @Test
    fun `a pending food the catalogue publishes stops being custom, and the row survives`() {
        // The row is kept rather than deleted: meals already reference its id, and losing the row would
        // turn those meals into an unresolvable id.
        val food = resolveFood(
            "callaloo",
            published = claim("Callaloo", "Vegetable"),
            pending = claim("Callaloo", "Custom"),
        )!!

        assertEquals("Callaloo", food.name)
        assertEquals("Vegetable", food.category)
        assertTrue("the catalogue speaks for it now", !food.custom)
    }

    @Test
    fun `a local rename wins for display without touching the catalogue`() {
        val food = resolveFood(
            "courgette",
            seeded = claim("Courgette", "Vegetable"),
            local = LocalFoodOpinion(localName = "Zucchini", hidden = false, lastUsedAt = 7_000L),
        )!!

        assertEquals("Courgette", food.name)
        assertEquals("Zucchini", food.localName)
        assertEquals(7_000L, food.lastUsedAt)
        assertEquals("Zucchini", displayFoodName(food.name, food.localName))
    }

    @Test
    fun `hiding is carried, not decided, by the merge`() {
        val hidden = resolveFood(
            "banana",
            seeded = claim("Banana", "Fruit"),
            local = LocalFoodOpinion(localName = null, hidden = true, lastUsedAt = null),
        )!!

        assertTrue(hidden.hidden)
    }

    @Test
    fun `an id no source knows is not created`() {
        assertNull(resolveFood("nothing", local = LocalFoodOpinion(null, true, null)))
    }

    @Test
    fun `a blank local rename falls back to the catalogue name`() {
        // A rename the parent cleared is not a name. It must not make a food unreadable in the picker.
        assertEquals("Banana", displayFoodName("Banana", "  "))
        assertEquals("Banana", displayFoodName("Banana", null))
    }
}
