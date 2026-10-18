package app.olauncher.helper

import app.olauncher.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeStatusTest {
    @Test
    fun `fallback coordinates are parsed`() {
        assertEquals(52.52 to 13.405, parseLatLon("52.52, 13.405"))
    }

    @Test
    fun `missing or broken fallback is ignored`() {
        assertNull(parseLatLon(""))
        assertNull(parseLatLon("53.5"))
        assertNull(parseLatLon("53.5,abc"))
        assertNull(parseLatLon("1,2,3"))
    }

    @Test
    fun `weather codes map to icons`() {
        assertEquals(R.drawable.ic_weather_clear, weatherIconRes(0))
        assertEquals(R.drawable.ic_weather_cloudy, weatherIconRes(3))
        assertEquals(R.drawable.ic_weather_rain, weatherIconRes(81))
        assertEquals(R.drawable.ic_weather_snow, weatherIconRes(86))
        assertNull(weatherIconRes(42))
    }
}
