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

    @Test
    fun `lab ignores the gemma monitor`() {
        val latest = mapOf("1" to 1, "2" to 1, "7" to 0)
        assertEquals(HomelabStatus(lab = true, gemma = false), homelabStatus(latest, "7", gemmaConfigured = true))
        assertEquals(HomelabStatus(lab = false, gemma = null), homelabStatus(latest, null, gemmaConfigured = false))
    }

    @Test
    fun `missing gemma monitor counts as down`() {
        assertEquals(HomelabStatus(lab = true, gemma = false), homelabStatus(mapOf("1" to 1), null, gemmaConfigured = true))
    }

    @Test
    fun `empty status page is not ok`() {
        assertEquals(HomelabStatus(lab = false, gemma = null), homelabStatus(emptyMap(), null, gemmaConfigured = false))
    }
}
