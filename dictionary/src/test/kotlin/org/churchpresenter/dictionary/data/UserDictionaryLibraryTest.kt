package org.churchpresenter.dictionary.data

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [UserDictionaryLibrary] — imported dictionaries kept, listed, loaded and removed, in a folder of their own. */
class UserDictionaryLibraryTest {

    private lateinit var root: File
    private lateinit var library: UserDictionaryLibrary

    @BeforeTest
    fun createDir() {
        root = Files.createTempDirectory("cp-dictionary-library").toFile()
        library = UserDictionaryLibrary(File(root, "dictionaries"))
    }

    @AfterTest
    fun deleteDir() {
        root.deleteRecursively()
    }

    private fun source(name: String, text: String) = File(root, name).apply { writeText(text) }

    private val eastons = """{"name":"Easton's Bible Dictionary","entries":[
        {"word":"Aaron","definition":"The eldest son of Amram."},
        {"word":"Abba","definition":"Father."},
        {"word":"Abba","definition":"A second sense."}]}"""

    @Test
    fun `an empty library lists nothing and loads nothing`() {
        assertEquals(emptyList(), library.list())
        assertEquals(emptyList(), library.load("dict-missing"))
    }

    @Test
    fun `an import is listed, and loads with its entries keyed by dictionary and headword`() {
        val info = library.import(source("eastons.json", eastons))

        assertEquals("dict-easton-s-bible-dictionary", info.id)
        assertEquals("Easton's Bible Dictionary", info.name)
        assertEquals(3, info.entryCount)
        assertFalse(info.isLexicon)
        assertEquals(listOf(info), library.list())

        val entries = library.load(info.id)
        assertEquals(
            listOf("${info.id}:Aaron", "${info.id}:Abba", "${info.id}:Abba#2"),
            entries.map { it.number },
            "a repeated headword gets a key of its own",
        )
        assertTrue(entries.all { it.source == "Easton's Bible Dictionary" })
        assertEquals("Easton's Bible Dictionary", entries.first().shownReference)
    }

    @Test
    fun `the copy outlives the file it was imported from`() {
        val file = source("eastons.json", eastons)
        val info = library.import(file)
        file.delete()

        assertEquals(3, library.load(info.id).size)
    }

    @Test
    fun `a lexicon keeps its Strong's numbers and is marked as one`() {
        val info = library.import(
            source("thayer.tsv", "strongs\tlemma\tdefinition\nG25\tἀγαπάω\tto love\nG26\tἀγάπη\tlove\n"),
            name = "Thayer's",
        )

        assertTrue(info.isLexicon)
        assertEquals("Thayer's", info.name)
        val entries = library.load(info.id)
        assertEquals(listOf("G25", "G26"), entries.map { it.number })
        assertEquals("G26 · Thayer's", entries.last().shownReference)
    }

    @Test
    fun `two imports with one name get two ids`() {
        val first = library.import(source("a.json", eastons))
        val second = library.import(source("b.json", eastons))

        assertEquals(first.id + "-2", second.id)
        assertEquals(listOf(first, second), library.list())
    }

    @Test
    fun `removing a dictionary takes it out of the list and deletes its copy`() {
        val kept = library.import(source("a.json", eastons))
        val removed = library.import(source("b.json", eastons), name = "Smith's")

        library.remove(removed.id)

        assertEquals(listOf(kept), library.list())
        assertEquals(emptyList(), library.load(removed.id))
        assertFalse(File(root, "dictionaries/${removed.id}.json").exists())
    }

    @Test
    fun `a file with no entries is refused and nothing is added`() {
        assertFailsWith<IllegalArgumentException> { library.import(source("empty.json", "[]")) }
        assertEquals(emptyList(), library.list())
    }

    @Test
    fun `a damaged index or dictionary file reads as empty rather than failing`() {
        val info = library.import(source("a.json", eastons))
        File(root, "dictionaries/${info.id}.json").writeText("{not json")
        assertEquals(emptyList(), library.load(info.id))

        File(root, "dictionaries/index.json").writeText("{not json")
        assertEquals(emptyList(), library.list())
    }

    @Test
    fun `ids are file-safe, prefixed, and never empty`() {
        assertEquals("dict-hitchcock-s-names", UserDictionaryLibrary.uniqueId("Hitchcock's Names", emptySet()))
        assertEquals("dict-dictionary", UserDictionaryLibrary.uniqueId("Словарь", emptySet()))
        assertEquals("dict-x-3", UserDictionaryLibrary.uniqueId("x", setOf("dict-x", "dict-x-2")))
    }
}
