package app.babylog.data.sync

import app.babylog.data.local.FoodEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodCatalogueDocumentsTest {
    @Test
    fun `catalogue parses version and entries, skipping a malformed row without losing the rest`() {
        val parsed = catalogueFromMap(
            mapOf(
                "version" to "2026-11.1",
                "foods" to listOf(
                    mapOf("id" to "callaloo", "name" to "Callaloo", "category" to "Vegetable"),
                    mapOf("id" to "broken", "category" to "Other"), // no name
                    mapOf("id" to "banana", "name" to "Banana"), // category defaults
                ),
            ),
        )

        assertEquals("2026-11.1", parsed.version)
        assertEquals(2, parsed.foods.size)
        assertEquals("Vegetable", parsed.foods.first { it.first == "callaloo" }.second.category)
        assertEquals("Other", parsed.foods.first { it.first == "banana" }.second.category)
    }

    @Test
    fun `empty or malformed catalogue is an empty catalogue rather than a failure`() {
        assertTrue(catalogueFromMap(emptyMap()).foods.isEmpty())
        assertTrue(catalogueFromMap(mapOf("version" to "v1", "foods" to "not a list")).foods.isEmpty())
    }

    @Test
    fun `pending addition carries a readable name but never local picker opinions`() {
        val map = pendingMap(
            FoodEntity(
                id = "callaloo",
                name = "Callaloo",
                category = "Custom",
                lastUsedAt = 42,
                custom = true,
                localName = "Kallaloo",
                hidden = true,
            ),
            at = 123,
        )

        assertEquals("Callaloo", map["name"])
        assertEquals(123L, map["addedAt"])
        assertFalse(map.containsKey("lastUsedAt"))
        assertFalse(map.containsKey("hidden"))
        assertFalse(map.containsKey("localName"))
    }

    @Test
    fun `pending document id is fallback and a missing name is rejected`() {
        assertEquals(
            "callaloo",
            pendingFromMap("callaloo", mapOf("name" to "Callaloo"))?.id,
        )
        assertNull(pendingFromMap("callaloo", mapOf("id" to "callaloo")))
    }

    @Test
    fun `rename proposal records the catalogue name and local suggestion`() {
        val map = proposalMap("courgette", "Courgette", "Zucchini", 99)

        assertEquals("courgette", map["id"])
        assertEquals("Courgette", map["from"])
        assertEquals("Zucchini", map["to"])
        assertEquals(99L, map["proposedAt"])
        assertFalse(map.containsKey("accepted"))
    }
}
