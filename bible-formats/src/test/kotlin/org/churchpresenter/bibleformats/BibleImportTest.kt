package org.churchpresenter.bibleformats

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [BibleImport] — one entry point that picks the right reader for a scripture file by its contents. */
class BibleImportTest {

    private lateinit var dir: File

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-bible-import-test").toFile()
    }

    @AfterTest
    fun deleteDir() {
        dir.deleteRecursively()
    }

    private fun file(name: String, text: String) = File(dir, name).apply { writeText(text) }

    private val zefania = """<?xml version="1.0"?><XMLBIBLE biblename="Z"><BIBLEBOOK bnumber="69" bname="Tobit">
        <CHAPTER cnumber="1"><VERS vnumber="1">Zefania Tobit</VERS></CHAPTER></BIBLEBOOK></XMLBIBLE>"""
    private val osis = """<?xml version="1.0"?><osis><osisText><div type="book" osisID="Jub">
        <verse osisID="Jub.1.1">OSIS Jubilees</verse></div></osisText></osis>"""
    private val usfx = """<?xml version="1.0"?><usfx>
        <book id="ENO"><c id="1"/><v id="1"/>USFX Enoch<ve/></book></usfx>"""

    @Test
    fun `each format is recognised by its root element`() {
        assertEquals(BibleImport.Format.ZEFANIA, BibleImport.detect(file("a.xml", zefania)))
        assertEquals(BibleImport.Format.OSIS, BibleImport.detect(file("b.xml", osis)))
        assertEquals(BibleImport.Format.USFX, BibleImport.detect(file("c.xml", usfx)))
        assertEquals(BibleImport.Format.PLAIN_TEXT, BibleImport.detect(file("Jasher.txt", "1 And God said")))
    }

    @Test
    fun `each format parses to its books`() {
        assertEquals(listOf(69), BibleImport.parse(file("a.xml", zefania)).books.map { it.number })
        assertEquals(listOf(103), BibleImport.parse(file("b.xml", osis)).books.map { it.number })
        assertEquals(listOf(100), BibleImport.parse(file("c.xml", usfx)).books.map { it.number })
        val text = BibleImport.parse(file("Jasher.txt", "1 And God said"))
        assertEquals(listOf(104 to "Jasher"), text.books.map { it.number to it.name })
    }

    @Test
    fun `convert writes an spb module carrying the extra book`() {
        val out = File(dir, "out.spb")
        BibleImport.convert(file("b.xml", osis), out)

        val spb = out.readText()
        assertTrue(spb.lines().any { it.startsWith("103\t") }, "the book header names Jubilees' number")
        assertTrue(spb.contains("B103C001V001"), "the verse carries its canon code")
    }

    @Test
    fun `a broken xml file stays XML so its parse error is reported`() {
        val broken = file("bad.xml", "this is not xml")

        assertEquals(BibleImport.Format.ZEFANIA, BibleImport.detect(broken))
        assertFailsWith<Exception> { BibleImport.parse(broken) }
    }

    @Test
    fun `a text file with no numbered verses is refused rather than written empty`() {
        val error = assertFailsWith<IllegalArgumentException> {
            BibleImport.parse(file("notes.txt", "Just a preface.\nNothing numbered."))
        }

        assertTrue(error.message.orEmpty().contains("notes.txt"))
    }

    @Test
    fun `an empty or non-XML file has no root element`() {
        assertNull(BibleImport.rootElementOf(file("empty.xml", "")))
        assertNull(BibleImport.rootElementOf(file("text.txt", "not < xml")))
    }
}
