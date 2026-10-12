package app.babylog.domain

import org.junit.Assert.assertEquals
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
    fun `one resolves to just that child`() {
        assertEquals(listOf("second-twin"), ChildSelection.One("second-twin").resolve(bothIds))
    }

    @Test
    fun `a selection for a child that no longer exists falls back to both`() {
        // Otherwise deleting a child leaves an empty screen with no explanation.
        assertEquals(bothIds, ChildSelection.One("deleted-child").resolve(bothIds))
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
}
