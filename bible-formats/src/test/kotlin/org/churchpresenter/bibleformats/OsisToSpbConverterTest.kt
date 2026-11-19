package org.churchpresenter.bibleformats

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [OsisToSpbConverter] — OSIS in, the same [ParsedBible] Zefania and USFX produce out. Every test
 * writes a real file in its own temp folder and parses it.
 */
class OsisToSpbConverterTest {

    private lateinit var dir: File

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-osis-test").toFile()
    }

    @AfterTest
    fun deleteDir() {
        dir.deleteRecursively()
    }

    private fun osis(body: String, header: String = ""): File = File(dir, "module.osis.xml").apply {
        writeText(
            """<?xml version="1.0" encoding="UTF-8"?>
            <osis xmlns="http://www.bibletechnologies.net/2003/OSIS/namespace">
            <osisText osisIDWork="Test" xml:lang="en">
            <header>$header</header>
            $body
            </osisText></osis>"""
        )
    }

    private fun texts(parsed: ParsedBible, book: Int = parsed.books.first().number): List<String> =
        parsed.books.first { it.number == book }.chapters
            .flatMap { c -> c.verses.map { "${c.number}:${it.number} ${it.text}" } }

    @Test
    fun `container verses are read under their book chapter and verse`() {
        val parsed = OsisToSpbConverter.parse(
            osis(
                """<div type="book" osisID="Gen"><chapter osisID="Gen.1">
                <verse osisID="Gen.1.1">In the beginning</verse>
                <verse osisID="Gen.1.2">And the earth</verse>
                </chapter></div>"""
            )
        )

        assertEquals(listOf(1), parsed.books.map { it.number })
        assertEquals(listOf("1:1 In the beginning", "1:2 And the earth"), texts(parsed))
    }

    @Test
    fun `milestone verses run from their start to their end marker`() {
        val parsed = OsisToSpbConverter.parse(
            osis(
                """<div type="book" osisID="1En"><chapter sID="1En.1" osisID="1En.1"/>
                <verse sID="1En.1.1" osisID="1En.1.1"/>The words of the blessing<verse eID="1En.1.1"/>
                <verse sID="1En.1.2" osisID="1En.1.2"/>And he took up his parable<verse eID="1En.1.2"/>
                <chapter eID="1En.1"/></div>"""
            )
        )

        assertEquals(listOf(100), parsed.books.map { it.number })
        assertEquals("1 Enoch", parsed.books.single().name)
        assertEquals(listOf("1:1 The words of the blessing", "1:2 And he took up his parable"), texts(parsed))
    }

    @Test
    fun `a note inside a verse is not spliced into its text`() {
        val parsed = OsisToSpbConverter.parse(
            osis(
                """<div type="book" osisID="Tob">
                <verse osisID="Tob.1.1">The book<note>a footnote</note> of Tobit</verse></div>"""
            )
        )

        assertEquals(listOf("1:1 The book of Tobit"), texts(parsed))
        assertEquals(69, parsed.books.single().number)
    }

    @Test
    fun `a heading inside a verse is left out`() {
        val parsed = OsisToSpbConverter.parse(
            osis(
                """<div type="book" osisID="Jub">
                <verse osisID="Jub.1.1"><title>Heading</title>And it came to pass</verse></div>"""
            )
        )

        assertEquals(listOf("1:1 And it came to pass"), texts(parsed))
    }

    @Test
    fun `the book's own title names it and the header names the work`() {
        val parsed = OsisToSpbConverter.parse(
            osis(
                body = """<div type="book" osisID="Jasher"><title type="main">The Book of Jasher</title>
                <verse osisID="Jasher.1.1">And God said</verse></div>""",
                header = """<work osisWork="Test"><title>Lost Books Collection</title>
                <rights>Public Domain</rights></work>""",
            )
        )

        assertEquals("Lost Books Collection", parsed.name)
        assertEquals("Public Domain", parsed.rights)
        assertEquals("EN", parsed.language)
        assertEquals(104, parsed.books.single().number)
        assertEquals("The Book of Jasher", parsed.books.single().name)
    }

    @Test
    fun `a book no table knows is kept under a number past the canon`() {
        val parsed = OsisToSpbConverter.parse(
            osis(
                """<div type="book" osisID="Scroll"><verse osisID="Scroll.1.1">first</verse></div>
                <div type="book" osisID="Other"><verse osisID="Other.2.3">second</verse></div>"""
            )
        )

        assertEquals(listOf(900, 901), parsed.books.map { it.number })
        assertEquals(listOf("Scroll", "Other"), parsed.books.map { it.name })
    }

    @Test
    fun `an empty verse and a malformed id are left out`() {
        val parsed = OsisToSpbConverter.parse(
            osis(
                """<div type="book" osisID="Gen"><verse osisID="Gen.1.1">  </verse>
                <verse osisID="Gen.x.2">bad chapter</verse>
                <verse osisID="Gen">no chapter</verse>
                <verse osisID="Gen.1.3-4">a range</verse></div>"""
            )
        )

        assertEquals(listOf("1:3 a range"), texts(parsed))
    }

    @Test
    fun `the caller's name and language win over the file's`() {
        val parsed = OsisToSpbConverter.parse(
            osis("""<div type="book" osisID="Gen"><verse osisID="Gen.1.1">x</verse></div>"""),
            name = "My Import",
            language = "deu",
        )

        assertEquals("My Import", parsed.name)
        assertEquals("DEU", parsed.language)
    }

    @Test
    fun `a milestone verse named only by its start id is still placed`() {
        val parsed = OsisToSpbConverter.parse(
            osis("""<div type="book" osisID="Sir"><verse sID="Sir.2.1"/>My son<verse eID="Sir.2.1"/></div>""")
        )

        assertEquals(listOf("2:1 My son"), texts(parsed))
    }

    @Test
    fun `a verse with no id at all is not read into the one before it`() {
        val parsed = OsisToSpbConverter.parse(
            osis(
                """<div type="book" osisID="Gen"><verse osisID="Gen.1.1">kept</verse>
                <verse>nameless</verse></div>"""
            )
        )

        assertEquals(listOf("1:1 kept"), texts(parsed))
    }

    @Test
    fun `a chapter title does not rename the book and a book with no title takes its English name`() {
        val parsed = OsisToSpbConverter.parse(
            osis(
                """<div type="book" osisID="1Macc"><title type="chapter">Chapter One</title>
                <verse osisID="1Macc.1.1">After Alexander</verse></div>"""
            )
        )

        assertEquals("1 Maccabees", parsed.books.single().name)
    }

    @Test
    fun `a note inside a note is skipped as a whole and a book with no verses is left out`() {
        val parsed = OsisToSpbConverter.parse(
            osis(
                """<div type="book" osisID="Bar"><title> </title></div>
                <div type="book" osisID="Wis"><verse osisID="Wis.1.1">Love
                <note>outer <note>inner</note> still note</note> righteousness</verse></div>"""
            )
        )

        assertEquals(listOf(68), parsed.books.map { it.number })
        assertEquals(listOf("1:1 Love righteousness"), texts(parsed))
    }

    @Test
    fun `a file with no work title or language is named after itself`() {
        val file = File(dir, "Jasher Gordon.xml").apply {
            writeText(
                """<osis><osisText><div type="book" osisID="Jasher">
                <verse osisID="Jasher.1.1">x</verse></div></osisText></osis>"""
            )
        }
        val parsed = OsisToSpbConverter.parse(file)

        assertEquals("Jasher Gordon", parsed.name)
        assertEquals(null, parsed.language)
    }

    @Test
    fun `OSIS ids resolve by table and by name`() {
        assertEquals(19, OsisToSpbConverter.bookNumberFor("Ps"))
        assertEquals(85, OsisToSpbConverter.bookNumberFor("PrAzar"))
        assertEquals(103, OsisToSpbConverter.bookNumberFor("Jubilees"))
        assertNull(OsisToSpbConverter.bookNumberFor("Nothing"))
    }
}
