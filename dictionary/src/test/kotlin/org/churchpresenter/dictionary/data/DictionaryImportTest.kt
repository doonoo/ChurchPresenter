package org.churchpresenter.dictionary.data

import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [DictionaryImport] — every dictionary format, each written as a real file and read back. */
class DictionaryImportTest {

    private lateinit var dir: File

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-dictionary-import").toFile()
    }

    @AfterTest
    fun deleteDir() {
        dir.deleteRecursively()
    }

    private fun file(name: String, text: String) = File(dir, name).apply { writeText(text) }

    private fun sqlite(name: String, vararg statements: String): File {
        val file = File(dir, name)
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { db ->
            db.createStatement().use { st -> statements.forEach(st::execute) }
        }
        return file
    }

    @Test
    fun `the app's own Strong's JSON imports as it is`() {
        val read = DictionaryImport.read(
            file(
                "strongs.json",
                """[{"number":"G26","word":"ἀγάπη","transliteration":"agápē","pronunciation":"ag-ah'-pay",
                "definition":"love","kjvUsage":"charity, love"}]""",
            )
        )

        assertEquals("strongs", read.name)
        val entry = read.entries.single()
        assertEquals("G26", entry.key)
        assertEquals("ἀγάπη", entry.headword)
        assertEquals("agápē", entry.transliteration)
        assertEquals("charity, love", entry.usage)
    }

    @Test
    fun `a JSON object names itself and its field names are read loosely`() {
        val read = DictionaryImport.read(
            file(
                "eastons.json",
                """{"name":"Easton's Bible Dictionary","language":"en","entries":[
                {"Term":"Aaron","Description":"<p>The eldest son of Amram.</p>"},
                {"Term":"","Description":""}, "not an object"]}""",
            )
        )

        assertEquals("Easton's Bible Dictionary", read.name)
        assertEquals("en", read.language)
        assertEquals(listOf(ImportedEntry("", "Aaron", "The eldest son of Amram.")), read.entries)
    }

    @Test
    fun `CSV keeps commas and line breaks inside quoted definitions`() {
        val read = DictionaryImport.read(
            file("names.csv", "headword,definition\nAbba,\"Father, in Aramaic\"\nAbel,\"Breath;\nvanity\"\n")
        )

        assertEquals(listOf("Father, in Aramaic", "Breath;\nvanity"), read.entries.map { it.definition })
    }

    @Test
    fun `TSV is told apart from CSV by its header`() {
        val read = DictionaryImport.read(file("lexicon.txt", "Strongs\tLemma\tGloss\ng0026\tἀγάπη\tlove, charity\n"))

        assertEquals(listOf(ImportedEntry("G26", "ἀγάπη", "love, charity")), read.entries)
    }

    @Test
    fun `a Zefania dictionary reads its title and its items`() {
        val read = DictionaryImport.read(
            file(
                "zef.xml",
                """<?xml version="1.0"?><dictionary><INFORMATION><title>Thayer's Lexicon</title>
                <language>ENG</language></INFORMATION>
                <item id="G25"><title>ἀγαπάω</title><transliteration>agapaō</transliteration>
                <description>to <b>love</b> dearly</description></item></dictionary>""",
            )
        )

        assertEquals("Thayer's Lexicon", read.name)
        assertEquals("ENG", read.language)
        assertEquals(
            ImportedEntry("G25", "ἀγαπάω", "to love dearly", transliteration = "agapaō"),
            read.entries.single(),
        )
    }

    @Test
    fun `a MySword dictionary reads its HTML entries and its title`() {
        val read = DictionaryImport.read(
            sqlite(
                "eastons.dct.mybible",
                "CREATE TABLE details (title TEXT, language TEXT)",
                "INSERT INTO details VALUES ('Easton''s Bible Dictionary', 'en')",
                "CREATE TABLE dictionary (id INTEGER, relativeorder INTEGER, word TEXT, data TEXT)",
                "INSERT INTO dictionary VALUES (1, 1, 'Aaron', '<p>The eldest son of Amram.</p>')",
            )
        )

        assertEquals("Easton's Bible Dictionary", read.name)
        assertEquals("en", read.language)
        assertEquals(listOf(ImportedEntry("Aaron", "Aaron", "The eldest son of Amram.")), read.entries)
    }

    @Test
    fun `an e-Sword lexicon reads its RTF entries keyed by Strong's number`() {
        val read = DictionaryImport.read(
            sqlite(
                "thayer.lexx",
                "CREATE TABLE Details (Title TEXT)",
                "INSERT INTO Details VALUES ('Thayer')",
                "CREATE TABLE Lexicon (Topic TEXT, Definition TEXT)",
                """INSERT INTO Lexicon VALUES ('G0026', '{\b agape}\par love')""",
            )
        )

        assertEquals("Thayer", read.name)
        assertEquals(listOf(ImportedEntry("G26", "", "agape\nlove")), read.entries)
    }

    @Test
    fun `a MyBible dictionary takes its title from the info table`() {
        val read = DictionaryImport.read(
            sqlite(
                "names.dictionary.SQLite3",
                "CREATE TABLE info (name TEXT, value TEXT)",
                "INSERT INTO info VALUES ('description', 'Hitchcock''s Names')",
                "CREATE TABLE dictionary (topic TEXT, definition TEXT)",
                "INSERT INTO dictionary VALUES ('Abel', 'vanity; breath')",
            )
        )

        assertEquals("Hitchcock's Names", read.name)
        assertEquals("", read.language)
        assertEquals("vanity; breath", read.entries.single().definition)
    }

    @Test
    fun `a SQLite file with no entries table is refused`() {
        val file = sqlite("bible.db", "CREATE TABLE verses (book INTEGER, text TEXT)")

        val error = assertFailsWith<IllegalArgumentException> { DictionaryImport.read(file) }
        assertTrue(error.message.orEmpty().contains("bible.db"))
    }

    @Test
    fun `Strong's keys are normalized and anything else is not one`() {
        assertEquals("G26", DictionaryImport.normalizeStrongs("g0026"))
        assertEquals("H1254a", DictionaryImport.normalizeStrongs(" H1254a "))
        assertNull(DictionaryImport.normalizeStrongs("Hagar"))
        assertNull(DictionaryImport.normalizeStrongs("G0"))
    }
}
