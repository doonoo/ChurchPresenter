package org.churchpresenter.bibletab

import org.churchpresenter.bible.CanonSection
import org.churchpresenter.bible.CanonSection.DEUTEROCANON
import org.churchpresenter.bible.CanonSection.NEW_TESTAMENT
import org.churchpresenter.bible.CanonSection.NT_APOCRYPHA
import org.churchpresenter.bible.CanonSection.OLD_TESTAMENT
import org.churchpresenter.bible.CanonSection.OT_PSEUDEPIGRAPHA
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [bookSectionHeaders] — where the book column puts a section heading, and when it puts none. */
class BibleBookSectionsTest {

    private val books = listOf("Genesis", "Matthew", "Tobit", "Judith", "1 Enoch", "Thomas", "Scroll")
    private val sections: List<CanonSection?> =
        listOf(OLD_TESTAMENT, NEW_TESTAMENT, DEUTEROCANON, DEUTEROCANON, OT_PSEUDEPIGRAPHA, NT_APOCRYPHA, null)

    @Test
    fun `an ordinary Bible gets no headings at all`() {
        val plain = listOf("Genesis", "John")
        val headers = bookSectionHeaders(plain, plain, listOf(OLD_TESTAMENT, NEW_TESTAMENT))

        assertTrue(headers.isEmpty(), "the 66 alone must look exactly as they always have")
    }

    @Test
    fun `a module with extra books heads each section at its first book`() {
        val headers = bookSectionHeaders(books, books, sections)

        assertEquals(
            mapOf(
                0 to OLD_TESTAMENT,
                1 to NEW_TESTAMENT,
                2 to DEUTEROCANON,
                4 to OT_PSEUDEPIGRAPHA,
                5 to NT_APOCRYPHA,
                6 to null,
            ),
            headers,
        )
    }

    @Test
    fun `a filtered list heads the sections that are left, by their position in the filter`() {
        val headers = bookSectionHeaders(books, listOf("Judith", "1 Enoch"), sections)

        assertEquals(mapOf(0 to DEUTEROCANON, 1 to OT_PSEUDEPIGRAPHA), headers)
    }

    @Test
    fun `a book the canon does not list still counts as an extra book`() {
        val withScroll = listOf("Genesis", "Scroll")
        val headers = bookSectionHeaders(withScroll, withScroll, listOf(OLD_TESTAMENT, null))

        assertEquals(mapOf(0 to OLD_TESTAMENT, 1 to null), headers)
    }

    @Test
    fun `sections that have not arrived yet give no headings`() {
        assertTrue(bookSectionHeaders(books, books, emptyList()).isEmpty())
    }
}
