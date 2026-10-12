package app.olauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkTest {
    @Test
    fun `full url is kept`() {
        assertEquals("https://zeit.de/a?b=1", linkUrl(" https://zeit.de/a?b=1 "))
        assertEquals("http://192.168.0.1:8080", linkUrl("http://192.168.0.1:8080"))
    }

    @Test
    fun `bare domain gets https`() {
        assertEquals("https://zeit.de", linkUrl("zeit.de"))
        assertEquals("https://www.example.com/path", linkUrl("www.example.com/path"))
    }

    @Test
    fun `app search text is not a link`() {
        assertNull(linkUrl("firefox"))
        assertNull(linkUrl("v1.2"))
        assertNull(linkUrl("zeit .de"))
        assertNull(linkUrl("https://"))
        assertNull(linkUrl(""))
    }
}
