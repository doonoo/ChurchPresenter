package org.churchpresenter.bible

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [BibleCanon] — the book numbers the app knows, which are written into every `.spb` a converter
 * produces. A number that moves or repeats renames a book in files users already have, so the
 * table's shape is pinned here as much as its lookups.
 */
class BibleCanonTest {

    @Test
    fun `no two books share a number or a code`() {
        val ids = BibleCanon.books.map { it.id }
        val codes = BibleCanon.books.map { it.usfm }
        assertEquals(ids.size, ids.toSet().size, "duplicate book numbers")
        assertEquals(codes.size, codes.toSet().size, "duplicate book codes")
    }

    @Test
    fun `every number fits the three digits of a verse code`() {
        assertTrue(BibleCanon.books.all { it.id in 1..999 })
    }

    @Test
    fun `the first sixty-six are the two testaments in the order spb files have always used`() {
        val first = BibleCanon.books.take(66)
        assertEquals((1..66).toList(), first.map { it.id })
        assertEquals("Genesis", first.first().englishName)
        assertEquals("Malachi", BibleCanon.englishName(39))
        assertEquals("Matthew", BibleCanon.englishName(40))
        assertEquals("Revelation", first.last().englishName)
        assertTrue(first.take(39).all { it.section == CanonSection.OLD_TESTAMENT })
        assertTrue(first.drop(39).all { it.section == CanonSection.NEW_TESTAMENT })
    }

    @Test
    fun `books past sixty-six are never filed under either testament`() {
        assertTrue(BibleCanon.books.drop(66).none { it.section.isProtestantCanon })
    }

    @Test
    fun `the deuterocanon keeps the Zefania numbers`() {
        assertEquals("JDT", BibleCanon.book(67)?.usfm)
        assertEquals("TOB", BibleCanon.book(69)?.usfm)
        assertEquals("SIR", BibleCanon.book(70)?.usfm)
        assertEquals("1MA", BibleCanon.book(72)?.usfm)
        assertEquals("2ES", BibleCanon.book(81)?.usfm)
    }

    @Test
    fun `the lost books are in their sections`() {
        assertEquals(CanonSection.DEUTEROCANON, BibleCanon.resolve("Tobit")?.section)
        assertEquals(CanonSection.OT_PSEUDEPIGRAPHA, BibleCanon.resolve("Enoch")?.section)
        assertEquals(CanonSection.OT_PSEUDEPIGRAPHA, BibleCanon.resolve("Jasher")?.section)
        assertEquals(CanonSection.OT_PSEUDEPIGRAPHA, BibleCanon.resolve("Jubilees")?.section)
        assertEquals(CanonSection.NT_APOCRYPHA, BibleCanon.resolve("Gospel of Thomas")?.section)
        assertEquals(CanonSection.NT_APOCRYPHA, BibleCanon.resolve("Laodiceans")?.section)
        assertEquals(CanonSection.NT_APOCRYPHA, BibleCanon.resolve("Didache")?.section)
    }

    @Test
    fun `the twelve testaments run Reuben to Benjamin from their first number`() {
        val first = BibleCanon.TWELVE_TESTAMENTS_FIRST_ID
        assertEquals("Testament of Reuben", BibleCanon.englishName(first))
        assertEquals("Testament of Levi", BibleCanon.englishName(first + 2))
        assertEquals("Testament of Benjamin", BibleCanon.englishName(first + 11))
        assertEquals(first + 2, BibleCanon.resolve("T Levi")?.id)
        assertEquals(first + 3, BibleCanon.resolve("TJud")?.id)
    }

    @Test
    fun `a name resolves in any case with or without spaces and full stops`() {
        assertEquals(100, BibleCanon.resolve("1 Enoch")?.id)
        assertEquals(100, BibleCanon.resolve("1enoch")?.id)
        assertEquals(100, BibleCanon.resolve("  ENO ")?.id)
        assertEquals(68, BibleCanon.resolve("Wis.")?.id)
        assertEquals(70, BibleCanon.resolve("Ecclesiasticus")?.id)
        assertEquals(72, BibleCanon.resolve("1 Macc")?.id)
        assertEquals(43, BibleCanon.resolve("john")?.id)
    }

    @Test
    fun `a book's own name wins over another book's alias`() {
        // "Thomas" is an alias of the Gospel of Thomas; the Infancy Gospel's full name must still be its own.
        assertEquals("XITH", BibleCanon.resolve("Infancy Gospel of Thomas")?.usfm)
        assertEquals("XGTH", BibleCanon.resolve("Thomas")?.usfm)
    }

    @Test
    fun `codes are matched in any case`() {
        assertEquals(69, BibleCanon.byUsfm("tob")?.id)
        assertEquals(103, BibleCanon.byUsfm(" JUB ")?.id)
        assertNull(BibleCanon.byUsfm("ZZZ"))
    }

    @Test
    fun `an unknown number or name has no entry`() {
        assertNull(BibleCanon.book(999))
        assertNull(BibleCanon.sectionOf(999))
        assertNull(BibleCanon.englishName(0))
        assertNull(BibleCanon.resolve("Book of Nothing"))
    }

    @Test
    fun `only the two testaments are the protestant canon`() {
        assertTrue(CanonSection.OLD_TESTAMENT.isProtestantCanon)
        assertTrue(CanonSection.NEW_TESTAMENT.isProtestantCanon)
        assertFalse(CanonSection.DEUTEROCANON.isProtestantCanon)
        assertFalse(CanonSection.OT_PSEUDEPIGRAPHA.isProtestantCanon)
        assertFalse(CanonSection.NT_APOCRYPHA.isProtestantCanon)
    }
}
