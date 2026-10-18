package app.olauncher.helper

import app.olauncher.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeStatusTest {
    @Test
    fun `fallback coordinates are parsed`() {
        assertEquals(53.5756 to 10.0438, parseLatLon("53.5756, 10.0438"))
    }

    @Test
    fun `missing or broken fallback is ignored`() {
        assertNull(parseLatLon(""))
        assertNull(parseLatLon("53.5"))
        assertNull(parseLatLon("53.5,abc"))
        assertNull(parseLatLon("1,2,3"))
    }

    @Test
    fun `weather codes map to conditions`() {
        assertEquals(R.string.weather_clear, weatherConditionRes(0))
        assertEquals(R.string.weather_cloudy, weatherConditionRes(3))
        assertEquals(R.string.weather_rain, weatherConditionRes(81))
        assertEquals(R.string.weather_snow, weatherConditionRes(86))
        assertNull(weatherConditionRes(42))
    }
}
