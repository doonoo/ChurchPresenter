package org.churchpresenter.bibleformats

import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [SqliteBibleReader] — MySword, e-Sword and MyBible Bible modules, each built as a real SQLite file. */
class SqliteBibleReaderTest {

    private lateinit var dir: File

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-sqlite-bible").toFile()
    }

    @AfterTest
    fun deleteDir() {
        dir.deleteRecursively()
    }

    private fun module(name: String, vararg statements: String): File {
        val file = File(dir, name)
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { db ->
            db.createStatement().use { st -> statements.forEach(st::execute) }
        }
        return file
    }

    private fun ParsedBible.refs(): List<String> = books.flatMap { b ->
        b.chapters.flatMap { c -> c.verses.map { "${b.number} ${b.name} ${c.number}:${it.number} ${it.text}" } }
    }

    @Test
    fun `a MySword module reads its verses with notes and Strong's tags dropped`() {
        val bible = SqliteBibleReader.parse(
            module(
                "kjv.bbl.mybible",
                "CREATE TABLE Details (Title TEXT, Language TEXT, Copyright TEXT)",
                "INSERT INTO Details VALUES ('King James', 'eng', 'Public Domain')",
                "CREATE TABLE Bible (Book INT, Chapter INT, Verse INT, Scripture TEXT)",
                "INSERT INTO Bible VALUES (1, 1, 1, 'In the beginning<WH7225> God<WH430><RF>a note<Rf> created<CM>')",
                "INSERT INTO Bible VALUES (43, 3, 16, '<TS>Heading<Ts>For God so loved the world')",
                "INSERT INTO Bible VALUES (1, 1, 2, '<RF>only a note<Rf>')",
            )
        )

        assertEquals("King James", bible.name)
        assertEquals("ENG", bible.language)
        assertEquals("Public Domain", bible.rights)
        assertEquals(
            listOf("1 Genesis 1:1 In the beginning God created", "43 John 3:16 For God so loved the world"),
            bible.refs(),
            "a verse that was nothing but a note is left out",
        )
    }

    @Test
    fun `an e-Sword module's RTF verses come out as text`() {
        val bible = SqliteBibleReader.parse(
            module(
                "web.bblx",
                "CREATE TABLE Details (Title TEXT)",
                "INSERT INTO Details VALUES ('World English')",
                "CREATE TABLE Bible (Book INT, Chapter INT, Verse INT, Scripture TEXT)",
                """INSERT INTO Bible VALUES (19, 23, 1, '\cf6 Yahweh is my shepherd:\cf0  I shall lack nothing.')""",
            )
        )

        assertEquals(listOf("19 Psalms 23:1 Yahweh is my shepherd: I shall lack nothing."), bible.refs())
    }

    @Test
    fun `books past the sixty-six in a MySword module are kept unnamed, past the canon, in order`() {
        val bible = SqliteBibleReader.parse(
            module(
                "apoc.bbl.mybible",
                "CREATE TABLE Bible (Book INT, Chapter INT, Verse INT, Scripture TEXT)",
                "INSERT INTO Bible VALUES (70, 1, 1, 'second extra')",
                "INSERT INTO Bible VALUES (67, 1, 1, 'first extra')",
            )
        )

        assertEquals(listOf("900 Book 67 1:1 first extra", "901 Book 70 1:1 second extra"), bible.refs())
        assertEquals("apoc", bible.name, "no Details table: named after the file")
    }

    @Test
    fun `a MyBible module maps its book codes and names its extra books`() {
        val bible = SqliteBibleReader.parse(
            module(
                "lost.SQLite3",
                "CREATE TABLE info (name TEXT, value TEXT)",
                "INSERT INTO info VALUES ('description', 'Lost Books')",
                "INSERT INTO info VALUES ('language', 'en')",
                "CREATE TABLE books (book_number INT, short_name TEXT, long_name TEXT)",
                "INSERT INTO books VALUES (470, 'Мф', 'От Матфея')",
                "INSERT INTO books VALUES (170, 'Тов', 'Tobit')",
                "INSERT INTO books VALUES (999, 'X', 'Scroll of Somebody')",
                "CREATE TABLE verses (book_number INT, chapter INT, verse INT, text TEXT)",
                "INSERT INTO verses VALUES (470, 1, 1, 'The book<S>976</S> of the generation<f>note</f>')",
                "INSERT INTO verses VALUES (170, 1, 1, 'The book of the words of Tobit')",
                "INSERT INTO verses VALUES (999, 2, 3, 'unknown')",
                "INSERT INTO verses VALUES (730, 1, 1, 'The Revelation')",
            )
        )

        assertEquals("Lost Books", bible.name)
        assertEquals("EN", bible.language)
        assertEquals(
            listOf(
                "40 От Матфея 1:1 The book of the generation",
                "66 Revelation 1:1 The Revelation",
                "69 Tobit 1:1 The book of the words of Tobit",
                "900 Scroll of Somebody 2:3 unknown",
            ),
            bible.refs(),
        )
    }

    @Test
    fun `an older MyBible module names its books in books_all, and an unnamed book falls back`() {
        val bible = SqliteBibleReader.parse(
            module(
                "old.SQLite3",
                "CREATE TABLE books_all (book_number INT, long_name TEXT)",
                "INSERT INTO books_all VALUES (10, '  ')",
                "CREATE TABLE verses (book_number INT, chapter INT, verse INT, text TEXT)",
                "INSERT INTO verses VALUES (10, 1, 1, 'In the beginning')",
                "INSERT INTO verses VALUES (888, 1, 1, 'nameless')",
            ),
            name = "Given Name",
        )

        assertEquals("Given Name", bible.name)
        assertEquals(listOf("1 Genesis 1:1 In the beginning", "900 Book 888 1:1 nameless"), bible.refs())
    }

    @Test
    fun `a MySword module in a language with a curated table takes its book names from it`() {
        val bible = SqliteBibleReader.parse(
            module(
                "rst.bbl.mybible",
                "CREATE TABLE Details (Title TEXT, Language TEXT)",
                "INSERT INTO Details VALUES ('Синодальный', 'rus')",
                "CREATE TABLE Bible (Book INT, Chapter INT, Verse INT, Scripture TEXT)",
                "INSERT INTO Bible VALUES (1, 1, 1, 'В начале')",
            )
        )

        assertEquals(listOf("1 Бытие 1:1 В начале"), bible.refs())
    }

    @Test
    fun `a file that is not SQLite is not a Bible module`() {
        assertFalse(SqliteBibleReader.isBibleModule(File(dir, "text.txt").apply { writeText("not sqlite") }))
    }

    @Test
    fun `MyBible's codes for the sixty-six run Genesis to Revelation`() {
        assertEquals(1, SqliteBibleReader.canonNumberForMyBible(10))
        assertEquals(17, SqliteBibleReader.canonNumberForMyBible(190))
        assertEquals(39, SqliteBibleReader.canonNumberForMyBible(460))
        assertEquals(40, SqliteBibleReader.canonNumberForMyBible(470))
        assertEquals(45, SqliteBibleReader.canonNumberForMyBible(590))
        assertEquals(59, SqliteBibleReader.canonNumberForMyBible(520))
        assertEquals(66, SqliteBibleReader.canonNumberForMyBible(730))
        assertNull(SqliteBibleReader.canonNumberForMyBible(170))
    }

    @Test
    fun `a SQLite file with no verses table is refused and not taken for a Bible`() {
        val file = module("dict.mybible", "CREATE TABLE dictionary (word TEXT, data TEXT)")

        assertFalse(SqliteBibleReader.isBibleModule(file))
        assertFailsWith<IllegalArgumentException> { SqliteBibleReader.parse(file) }
    }

    @Test
    fun `BibleImport recognises a module by its SQLite header and converts it`() {
        val file = module(
            "kjv.bbl.mybible",
            "CREATE TABLE Bible (Book INT, Chapter INT, Verse INT, Scripture TEXT)",
            "INSERT INTO Bible VALUES (1, 1, 1, 'In the beginning')",
        )

        assertTrue(SqliteBibleReader.isBibleModule(file))
        assertEquals(BibleImport.Format.SQLITE, BibleImport.detect(file))
        val out = File(dir, "kjv.spb")
        BibleImport.convert(file, out)
        assertTrue(out.readText().contains("B001C001V001\t1\t1\t1\tIn the beginning"))
    }

    @Test
    fun `a module's verse markup is reduced to its words`() {
        assertEquals("a b", ModuleMarkup.fromVerse("a <i>b</i>"))
        assertEquals("word", ModuleMarkup.fromVerse("word<WG3056><WTN-NSM>"))
        assertEquals("plain", ModuleMarkup.fromVerse("plain"))
    }
}
