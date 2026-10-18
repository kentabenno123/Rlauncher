package app.olauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeFolderTest {
    private val app = FolderApp("Files", "com.example.files", "com.example.files.Main", "UserHandle{0}", "")
    private val link = FolderApp("Wiki, Docs", "app.rlauncher", "", "UserHandle{0}", "link-1")

    @Test
    fun `folder survives a round trip`() {
        assertEquals(listOf(app, link), decodeFolder(encodeFolder(listOf(app, link))))
    }

    @Test
    fun `empty folder stays empty`() {
        assertEquals(emptyList<FolderApp>(), decodeFolder(encodeFolder(emptyList())))
    }

    @Test
    fun `broken entries are skipped`() {
        assertEquals(listOf(app), decodeFolder(encodeFolder(listOf(app)) + "\u001Egarbage"))
    }
}
