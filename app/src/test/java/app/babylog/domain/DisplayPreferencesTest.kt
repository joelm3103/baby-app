package app.babylog.domain

import app.babylog.ui.home.*
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class DisplayPreferencesTest {
    @Test fun `twelve hour clock distinguishes midnight noon and evening`() {
        val zone = ZoneId.of("UTC")
        fun at(time: String) = Instant.parse("2026-10-23T${time}:00Z").toEpochMilli()
        assertEquals("12:00 AM", clockTime(at("00:00"), zone, TimeFormat.TWELVE_HOUR))
        assertEquals("12:00 PM", clockTime(at("12:00"), zone, TimeFormat.TWELVE_HOUR))
        assertEquals("9:05 PM", clockTime(at("21:05"), zone, TimeFormat.TWELVE_HOUR))
        assertEquals("21:05", clockTime(at("21:05"), zone))
    }
    @Test fun `Fahrenheit input converts to canonical Celsius and keeps fever boundary`() {
        val read = readHealth(HealthFields(temperature = "100.4"), unit = TemperatureUnit.FAHRENHEIT)
        assertNull(read.error)
        assertEquals(38.0, read.values.temperatureC!!, 0.000001)
        assertTrue(isFever(read.values.temperatureC))
        assertEquals("100.4 °F", formatTemperature(38.0, TemperatureUnit.FAHRENHEIT))
        assertNotNull(readHealth(HealthFields(temperature = "38"), unit = TemperatureUnit.FAHRENHEIT).error)
        assertNotNull(readHealth(HealthFields(temperature = "NaN"), unit = TemperatureUnit.FAHRENHEIT).error)
    }
    @Test fun `untouched Fahrenheit prefill preserves precise stored reading`() {
        val initial = HealthValues(temperatureC = 37.123456)
        val fields = healthFieldsFor(initial, TemperatureUnit.FAHRENHEIT)
        assertEquals(initial, readHealth(fields, initial, TemperatureUnit.FAHRENHEIT).values)
    }
}
