package app.babylog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ChildSelectionTest {

    private val bothIds = listOf("first-twin", "second-twin")

    @Test
    fun `both resolves to every child and is the default`() {
        assertEquals(bothIds, ChildSelection.Both.resolve(bothIds))
        assertEquals(ChildSelection.Both, ChildSelection.default)
        assertEquals(ChildSelection.Both, decodeChildSelection(null))
    }

    @Test
    fun `the default is not null`() {
        // Pins the bug that crashed 0.1.0 on a real device: a STORED `val default = Both` captured
        // null, because Android initialises this interface - and so its companion - while Both is
        // still being constructed, reading the INSTANCE field before it is assigned. HotSpot does not
        // do that, so this assertion passed even while the app died on launch. It documents intent;
        // the actual fix is that `default` is now a getter.
        assertNotNull(ChildSelection.default)
        assertSame(ChildSelection.Both, ChildSelection.default)
    }

    @Test
    fun `one resolves to just that child`() {
        assertEquals(listOf("second-twin"), ChildSelection.One("second-twin").resolve(bothIds))
    }

    @Test
    fun `a selection for a child that no longer exists falls back to the first child`() {
        // Otherwise removing a child leaves an empty screen with no explanation.
        assertEquals(listOf("first-twin"), ChildSelection.One("deleted-child").resolve(bothIds))
    }

    @Test
    fun `both resolves to every child only when there are exactly two`() {
        assertEquals(bothIds, ChildSelection.Both.resolve(bothIds))
        // One child: Both is a redundant pill. Three: it would silently mean "all of them".
        assertEquals(listOf("first-twin"), ChildSelection.Both.resolve(listOf("first-twin")))
        assertEquals(listOf("first-twin"), ChildSelection.Both.resolve(listOf("first-twin", "second-twin", "noah")))
    }

    @Test
    fun `fallbackFor is Both only at exactly two children`() {
        val first-twin = child("first-twin", order = 0)
        val second-twin = child("second-twin", order = 1)
        val noah = child("noah", order = 2)

        assertEquals(ChildSelection.Both, ChildSelection.fallbackFor(listOf(first-twin, second-twin)))
        assertEquals(ChildSelection.One("first-twin"), ChildSelection.fallbackFor(listOf(first-twin)))
        assertEquals(ChildSelection.One("first-twin"), ChildSelection.fallbackFor(listOf(first-twin, second-twin, noah)))
    }

    @Test
    fun `isValidFor rejects Both unless there are exactly two children`() {
        val first-twin = child("first-twin", order = 0)
        val second-twin = child("second-twin", order = 1)
        val noah = child("noah", order = 2)

        assertTrue(ChildSelection.Both.isValidFor(listOf(first-twin, second-twin)))
        assertTrue(!ChildSelection.Both.isValidFor(listOf(first-twin)))
        assertTrue(!ChildSelection.Both.isValidFor(listOf(first-twin, second-twin, noah)))
        assertTrue(ChildSelection.One("second-twin").isValidFor(listOf(first-twin, second-twin)))
        assertTrue(!ChildSelection.One("gone").isValidFor(listOf(first-twin, second-twin)))
    }

    @Test
    fun `encode and decode round trip`() {
        for (selection in listOf(ChildSelection.Both, ChildSelection.One("first-twin"), ChildSelection.One("second-twin"))) {
            assertEquals(selection, decodeChildSelection(encodeChildSelection(selection)))
        }
    }

    @Test
    fun `garbage decodes to the default rather than throwing`() {
        assertTrue(decodeChildSelection("") == ChildSelection.Both)
        assertTrue(decodeChildSelection("ONE:") == ChildSelection.Both)
        assertTrue(decodeChildSelection("WHATEVER") == ChildSelection.Both)
    }

    @Test
    fun `enumOrDefault tolerates unknown and unset values`() {
        assertEquals(ThemeMode.SYSTEM, enumOrDefault<ThemeMode>(null, ThemeMode.SYSTEM))
        assertEquals(ThemeMode.SYSTEM, enumOrDefault<ThemeMode>("NONSENSE", ThemeMode.SYSTEM))
        assertEquals(ThemeMode.DARK, enumOrDefault<ThemeMode>("DARK", ThemeMode.SYSTEM))
        assertEquals(VolumeUnit.FLUID_OUNCES, enumOrDefault<VolumeUnit>("FLUID_OUNCES", VolumeUnit.MILLILITRES))
    }

    @Test
    fun `unit family names survive a storage round trip`() {
        for (unit in LengthUnit.entries) {
            assertEquals(unit, enumOrDefault(unit.name, LengthUnit.CENTIMETRES))
        }
        for (unit in VolumeUnit.entries) {
            assertEquals(unit, enumOrDefault(unit.name, VolumeUnit.MILLILITRES))
        }
        assertEquals(UnitSystem.METRIC, UnitPreferences.Metric.system)
        assertEquals(UnitSystem.IMPERIAL, UnitPreferences.Imperial.system)
        assertEquals(
            UnitSystem.CUSTOM,
            UnitPreferences(LengthUnit.INCHES, WeightUnit.KILOGRAMS, VolumeUnit.MILLILITRES).system,
        )
    }

    private fun child(id: String, order: Int) = Child(
        id = id,
        name = id.replaceFirstChar { it.uppercase() },
        birthDate = java.time.LocalDate.of(2025, 12, 31),
        sex = Sex.BOY,
        sortOrder = order,
    )
}
