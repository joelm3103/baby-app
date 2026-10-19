package app.babylog.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ChildBadgesTest {

    private fun child(id: String, name: String, order: Int = 0) = Child(
        id = id,
        name = name,
        birthDate = LocalDate.of(2026, 1, 1),
        sex = Sex.BOY,
        sortOrder = order,
    )

    @Test
    fun `distinct initials stay a single letter`() {
        val badges = badgesFor(listOf(child("a", "First Twin", 0), child("b", "Second Twin", 1)))

        assertEquals(mapOf("a" to "F", "b" to "S"), badges)
    }

    @Test
    fun `two names sharing a letter are numbered in list order`() {
        val badges = badgesFor(listOf(child("a", "Kai", 0), child("b", "Kim", 1)))

        assertEquals(mapOf("a" to "K1", "b" to "K2"), badges)
    }

    @Test
    fun `three names sharing a letter continue the numbering`() {
        val badges = badgesFor(
            listOf(child("a", "Kai", 0), child("b", "Kim", 1), child("c", "Kit", 2)),
        )

        assertEquals(mapOf("a" to "K1", "b" to "K2", "c" to "K3"), badges)
    }

    @Test
    fun `each colliding group is numbered independently`() {
        val badges = badgesFor(
            listOf(
                child("a", "Kai", 0),
                child("b", "Lee", 1),
                child("c", "Kim", 2),
                child("d", "Lia", 3),
            ),
        )

        assertEquals(mapOf("a" to "K1", "b" to "L1", "c" to "K2", "d" to "L2"), badges)
    }

    @Test
    fun `a single child keeps the bare letter`() {
        assertEquals(mapOf("a" to "K"), badgesFor(listOf(child("a", "Kai"))))
    }

    @Test
    fun `collision is on the uppercased first letter`() {
        assertEquals(mapOf("a" to "K1", "b" to "K2"), badgesFor(listOf(child("a", "kai"), child("b", "Kai"))))
    }

    @Test
    fun `a blank name yields a question mark rather than an empty badge`() {
        assertEquals(mapOf("a" to "?"), badgesFor(listOf(child("a", "   "))))
    }

    @Test
    fun `the same list produces the same badges`() {
        val children = listOf(child("a", "Kai", 0), child("b", "Kim", 1))

        assertEquals(badgesFor(children), badgesFor(children))
    }
}
