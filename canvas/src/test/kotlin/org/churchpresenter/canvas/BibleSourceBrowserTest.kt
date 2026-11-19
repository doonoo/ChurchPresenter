package org.churchpresenter.canvas

import org.churchpresenter.bible.SpbFixture
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/** [BibleSourceBrowser]'s book list — every book the module carries, as the Bible tab lists them. */
class BibleSourceBrowserTest {

    @Test
    fun `a module's books past the sixty-six are offered too`() {
        val dir = Files.createTempDirectory("cp-canvas-bible-books").toFile()
        try {
            val books = (1..66).map { SpbFixture.Book(it, "Book $it", 1) } + SpbFixture.Book(100, "1 Enoch", 1)
            val content = SpbFixture.buildContent(
                title = "With Enoch",
                books = books,
                verses = listOf(SpbFixture.Verse(100, 1, 1, "The words of the blessing of Enoch.")),
            )
            val browser = BibleSourceBrowser(SpbFixture.loadedBible(dir, content))

            assertEquals(67, browser.books.size)
            assertEquals("1 Enoch", browser.books.last())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `no module offers no books`() {
        assertEquals(emptyList(), BibleSourceBrowser(null).books)
    }
}
