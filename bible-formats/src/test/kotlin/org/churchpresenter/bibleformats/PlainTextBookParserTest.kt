package org.churchpresenter.bibleformats

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [PlainTextBookParser] — a lost book typed out as text, in the shapes such files really come in. */
class PlainTextBookParserTest {

    private fun parse(text: String, book: String = "Jasher") =
        PlainTextBookParser.parse(text.trimIndent().lines(), book)

    private fun ParsedBible.refs(): List<String> = books.flatMap { b ->
        b.chapters.flatMap { c -> c.verses.map { "${b.number} ${c.number}:${it.number} ${it.text}" } }
    }

    @Test
    fun `a file with no heading is the caller's book, numbered from the canon`() {
        val parsed = parse(
            """
            Chapter 1
            1 And God said, Let us make man.
            2 And the Lord God formed man.
            Chapter 2
            1 And Adam knew his wife.
            """
        )

        assertEquals(listOf("Jasher"), parsed.books.map { it.name })
        assertEquals(
            listOf(
                "104 1:1 And God said, Let us make man.",
                "104 1:2 And the Lord God formed man.",
                "104 2:1 And Adam knew his wife.",
            ),
            parsed.refs(),
        )
    }

    @Test
    fun `roman chapter numbers and bracketed or dotted verse numbers are read`() {
        val parsed = parse(
            """
            CHAPTER XII
            [1] First.
            2. Second.
            Ch. 4
            3) Third.
            """
        )

        // Chapters come out in number order, whatever order the file wrote them in.
        assertEquals(listOf("104 4:3 Third.", "104 12:1 First.", "104 12:2 Second."), parsed.refs())
    }

    @Test
    fun `a full reference names its own chapter`() {
        val parsed = parse("4:3 Text of four three.\n5:1 Text of five one.")

        assertEquals(listOf("104 4:3 Text of four three.", "104 5:1 Text of five one."), parsed.refs())
    }

    @Test
    fun `a wrapped verse is carried on and a preface is dropped`() {
        val parsed = parse(
            """
            Translated from the Hebrew in 1840.
            1 And it came to pass
            that the earth was filled.
            2 And God saw.
            """
        )

        assertEquals(
            listOf("104 1:1 And it came to pass that the earth was filled.", "104 1:2 And God saw."),
            parsed.refs(),
        )
    }

    @Test
    fun `headings split one file into several books`() {
        val parsed = parse(
            """
            # 1 Enoch
            1:1 The words of the blessing of Enoch.
            Book: Jubilees
            1:1 This is the history of the division of the days.
            THE BOOK OF TOBIT
            1 The book of the words of Tobit.
            """,
            book = "ignored",
        )

        assertEquals(listOf(69, 100, 103), parsed.books.map { it.number })
        assertEquals(listOf("Tobit", "1 Enoch", "Jubilees"), parsed.books.map { it.name })
    }

    @Test
    fun `an unknown book keeps its name under a number past the canon`() {
        val parsed = parse("# The Scroll of Somebody\n1 First line.\n# Second Scroll\n1 Other.")

        assertEquals(listOf(900, 901), parsed.books.map { it.number })
        assertEquals(listOf("The Scroll of Somebody", "Second Scroll"), parsed.books.map { it.name })
    }

    @Test
    fun `returning to a book heading already seen adds to that book`() {
        val parsed = parse("# Scroll\n1 One.\n# Other\n1 X.\n# Scroll\nChapter 2\n1 Two.")

        assertEquals(listOf("900 1:1 One.", "900 2:1 Two.", "901 1:1 X."), parsed.refs())
    }

    @Test
    fun `a book of heading mid-verse is text unless it names a known book`() {
        val parsed = parse("1 He wrote the book of his fathers\nBook of his fathers was long.")

        assertEquals(1, parsed.books.size)
        assertTrue(parsed.refs().single().endsWith("Book of his fathers was long."))
    }

    @Test
    fun `a file with no verses has no books`() {
        val parsed = parse("Just a preface.\nNothing numbered.")

        assertTrue(parsed.books.isEmpty())
        assertEquals("Jasher", parsed.name)
    }

    @Test
    fun `a file on disk with a byte order mark reads the same`() {
        val dir = Files.createTempDirectory("cp-plaintext-test").toFile()
        try {
            val file = File(dir, "enoch.txt").apply { writeText("﻿1 The words of Enoch.") }
            val parsed = PlainTextBookParser.parse(file, "1 Enoch", name = "Enoch (Charles)", language = "eng")

            assertEquals(listOf("100 1:1 The words of Enoch."), parsed.refs())
            assertEquals("Enoch (Charles)", parsed.name)
            assertEquals("ENG", parsed.language)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `roman numerals convert and malformed ones are refused`() {
        assertEquals(12, PlainTextBookParser.romanToInt("XII"))
        assertEquals(4, PlainTextBookParser.romanToInt("iv"))
        assertEquals(1990, PlainTextBookParser.romanToInt("MCMXC"))
        assertNull(PlainTextBookParser.romanToInt("XQ"))
    }
}
