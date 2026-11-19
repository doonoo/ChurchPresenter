package org.churchpresenter.dictionary.data

import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The shapes real dictionary files drift into, read without failing and without inventing entries. */
class DictionaryImportEdgeCasesTest {

    private lateinit var dir: File

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-dictionary-edges").toFile()
    }

    @AfterTest
    fun deleteDir() {
        dir.deleteRecursively()
    }

    private fun file(name: String, text: String) = File(dir, name).apply { writeText(text) }

    @Test
    fun `JSON that is neither a list nor an object of entries reads as no entries`() {
        assertTrue(DictionaryImport.readJson("\"just text\"", "x").entries.isEmpty())
        assertEquals("x", DictionaryImport.readJson("""{"title":""}""", "x").name)
        assertTrue(DictionaryImport.readJson("""{"name":"N"}""", "x").entries.isEmpty())
    }

    @Test
    fun `a JSON field that is not text is passed over for the next name`() {
        val read = DictionaryImport.readJson("""[{"word":{"nested":1},"term":"Abel","definition":"vanity"}]""", "x")

        assertEquals("Abel", read.entries.single().headword)
    }

    @Test
    fun `an entry with only a definition is kept, and an explicit Strong's key wins over the headword`() {
        val read = DictionaryImport.readJson(
            """[{"definition":"a note with no headword"},{"number":"g0026","word":"agape","definition":"love"}]""",
            "x",
        )

        assertEquals(listOf("", "G26"), read.entries.map { it.key })
        assertEquals(listOf("", "agape"), read.entries.map { it.headword })
    }

    @Test
    fun `a comma-separated text file is read as CSV and an empty one as nothing`() {
        val csv = DictionaryImport.read(file("list.txt", "word,definition\nAbel,\"vanity, breath\"\n"))
        assertEquals("vanity, breath", csv.entries.single().definition)

        assertTrue(DictionaryImport.readDelimited("", "x", null).entries.isEmpty())
    }

    @Test
    fun `a Zefania dictionary without an INFORMATION block is named by its attribute or its file`() {
        val named = DictionaryImport.read(
            file(
                "a.xml",
                """<dictionary dictionaryname="Smith's"><item id="x"><title>Abel</title>
                <description>breath</description></item></dictionary>""",
            ),
        )
        assertEquals("Smith's", named.name)
        assertEquals("", named.language)

        val unnamed = DictionaryImport.read(
            file(
                "plain.xml",
                """<dictionary><item><title> </title>
                <description>only a definition</description></item></dictionary>""",
            ),
        )
        assertEquals("plain", unnamed.name)
        assertEquals(listOf(ImportedEntry("", "", "only a definition")), unnamed.entries)
    }

    @Test
    fun `a SQLite module with empty cells and no title takes its file's name`() {
        val file = File(dir, "bare.dct.mybible")
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { db ->
            db.createStatement().use { st ->
                st.execute("CREATE TABLE details (title TEXT)")
                st.execute("INSERT INTO details VALUES ('  ')")
                st.execute("CREATE TABLE dictionary (word TEXT, data TEXT)")
                st.execute("INSERT INTO dictionary VALUES (NULL, 'orphan definition')")
                st.execute("INSERT INTO dictionary VALUES ('Empty', NULL)")
                st.execute("INSERT INTO dictionary VALUES (NULL, NULL)")
            }
        }
        val read = DictionaryImport.read(file)

        assertEquals("bare", read.name)
        assertEquals(listOf("orphan definition", ""), read.entries.map { it.definition })
    }

    @Test
    fun `bare RTF and bare entities are both cleaned, and an entity past Unicode is kept as written`() {
        assertEquals("one\ntwo", DictionaryText.clean("""one\par two"""))
        assertEquals("&#99999999; < >", DictionaryText.clean("&#99999999; &lt; &gt;"))
    }

    @Test
    fun `a carriage return alone ends a row`() {
        assertEquals(listOf(listOf("a"), listOf("b")), DelimitedText.parse("a\rb", ','))
    }

    @Test
    fun `a blank name given to an import falls back to the file's own`() {
        val library = UserDictionaryLibrary(File(dir, "lib"))
        val source = file("n.json", """{"name":"Own Name","entries":[{"key":"K1","definition":"d"}]}""")
        val info = library.import(source, name = "  ")

        assertEquals("Own Name", info.name)
        assertEquals("${info.id}:K1", library.load(info.id).single().number, "no headword: keyed by its own key")
    }
}
