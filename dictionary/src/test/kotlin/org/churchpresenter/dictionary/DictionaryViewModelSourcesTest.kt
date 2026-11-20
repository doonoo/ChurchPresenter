package org.churchpresenter.dictionary

import kotlinx.coroutines.runBlocking
import org.churchpresenter.dictionary.data.UserDictionaryLibrary
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The view model's dictionary sources: Strong's, the dictionaries the operator imports, and the
 * lexicons a Strong's entry is matched in. Every library here is in a folder the test owns.
 */
class DictionaryViewModelSourcesTest {

    private lateinit var root: File
    private lateinit var library: UserDictionaryLibrary
    private val created = mutableListOf<DictionaryViewModel>()

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("cp-dictionary-sources").toFile()
        library = UserDictionaryLibrary(File(root, "dictionaries"))
    }

    @AfterTest
    fun tearDown() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
        root.deleteRecursively()
    }

    private fun source(name: String, text: String) = File(root, name).apply { writeText(text) }

    private val eastons = """{"name":"Easton's","entries":[
        {"word":"Aaron","definition":"The eldest son of Amram."},
        {"word":"Abba","definition":"Father."}]}"""

    private val thayer = "strongs\tlemma\tdefinition\nG26\tἀγάπη\taffection, good-will\n"

    private fun vm(): DictionaryViewModel =
        DictionaryViewModel(DictionaryFixture.files()) { library }.also { created += it }

    private fun awaitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("timed out waiting for $what")
            Thread.yield()
        }
    }

    /** Loads [vm]'s current source and waits for its entries. */
    private fun loadNow(vm: DictionaryViewModel) {
        vm.load()
        awaitUntil("the entries to load") { vm.entries.isNotEmpty() && !vm.isLoading }
    }

    @Test
    fun `Strong's is read until another dictionary is chosen`() {
        val d = vm()
        loadNow(d)

        assertNull(d.activeSourceId)
        assertNull(d.activeDictionary)
        assertTrue(d.isStrongsKeyed)
        assertTrue(d.entries.all { it.isStrongsNumber })
    }

    @Test
    fun `importing a dictionary lists it and switches to it`() {
        val d = vm()
        loadNow(d)
        runBlocking { d.importDictionary(source("eastons.json", eastons)).join() }

        assertEquals(listOf("Easton's"), d.userDictionaries.map { it.name })
        assertEquals("Easton's", d.activeDictionary?.name)
        assertFalse(d.isStrongsKeyed, "a topical dictionary has no Strong's numbers to filter on")
        loadNow(d)
        assertEquals(listOf("Aaron", "Abba"), d.entries.map { it.word })
        assertNull(d.importError)
    }

    @Test
    fun `an imported headword is found whatever its case`() {
        val d = vm()
        runBlocking { d.importDictionary(source("eastons.json", eastons)).join() }
        loadNow(d)

        d.searchQuery = "aaron"
        assertEquals(listOf("Aaron"), d.searchResults.map { it.word })
    }

    @Test
    fun `a file that is no dictionary leaves the reason and stays on Strong's`() {
        val d = vm()
        runBlocking { d.importDictionary(source("empty.json", "[]")).join() }

        assertEquals("empty.json", d.importError?.fileName)
        assertTrue(d.importError?.reason.orEmpty().contains("No dictionary entries"))
        assertNull(d.activeSourceId)
        assertFalse(d.isImporting)

        d.dismissImportError()
        assertNull(d.importError)
    }

    @Test
    fun `switching back to Strong's reads it again and clears the selection`() {
        val d = vm()
        runBlocking { d.importDictionary(source("eastons.json", eastons)).join() }
        loadNow(d)
        d.onEntrySelected(d.entries.first())

        d.selectSource(null)
        assertNull(d.selectedEntry)
        assertTrue(d.entries.isEmpty(), "the old dictionary's entries are dropped at once")
        loadNow(d)
        assertTrue(d.entries.all { it.isStrongsNumber })
    }

    @Test
    fun `choosing the source already chosen changes nothing`() {
        val d = vm()
        loadNow(d)
        val before = d.entries

        d.selectSource(null)
        assertEquals(before, d.entries)
    }

    @Test
    fun `removing the dictionary being read goes back to Strong's`() {
        val d = vm()
        runBlocking { d.importDictionary(source("eastons.json", eastons)).join() }
        val id = d.activeSourceId!!

        runBlocking { d.removeDictionary(id).join() }

        assertNull(d.activeSourceId)
        assertTrue(d.userDictionaries.isEmpty())
        assertTrue(library.list().isEmpty())
    }

    @Test
    fun `removing another dictionary keeps the one being read`() {
        val d = vm()
        runBlocking { d.importDictionary(source("thayer.tsv", thayer)).join() }
        val lexicon = d.activeSourceId!!
        runBlocking { d.importDictionary(source("eastons.json", eastons)).join() }
        val reading = d.activeSourceId

        runBlocking { d.removeDictionary(lexicon).join() }

        assertEquals(reading, d.activeSourceId)
        assertEquals(listOf("Easton's"), d.userDictionaries.map { it.name })
    }

    @Test
    fun `a lexicon is Strong's-keyed, and a Strong's entry shows what it says`() {
        val d = vm()
        runBlocking { d.importDictionary(source("thayer.tsv", thayer)).join() }
        assertTrue(d.isStrongsKeyed)

        d.selectSource(null)
        loadNow(d)
        val g26 = d.entries.first { it.number == "G26" }
        assertEquals(listOf("affection, good-will"), d.lexiconEntriesFor(g26).map { it.definition })
        assertTrue(d.lexiconEntriesFor(d.entries.first { it.number == "H430" }).isEmpty())
    }

    @Test
    fun `a lexicon's own entry lists no lexicons of its own`() {
        val d = vm()
        runBlocking { d.importDictionary(source("thayer.tsv", thayer)).join() }
        loadNow(d)

        assertTrue(d.lexiconEntriesFor(d.entries.single()).isEmpty())
    }

    @Test
    fun `dictionaries imported before the tab opened are listed on its first load`() {
        library.import(source("eastons.json", eastons))
        val d = vm()

        d.load()
        awaitUntil("the library to be listed") { d.userDictionaries.isNotEmpty() }
        assertEquals(listOf("Easton's"), d.userDictionaries.map { it.name })
    }

    @Test
    fun `openEntry switches to an imported entry's dictionary and selects it once read`() {
        val d = vm()
        runBlocking { d.importDictionary(source("eastons.json", eastons)).join() }
        val id = d.activeSourceId!!
        d.selectSource(null)
        loadNow(d)

        d.openEntry("$id:Abba")
        loadNow(d)

        assertEquals(id, d.activeSourceId)
        assertEquals("Abba", d.selectedEntry?.word)
    }

    @Test
    fun `openEntry within the dictionary being read selects the entry at once`() {
        val d = vm()
        runBlocking { d.importDictionary(source("eastons.json", eastons)).join() }
        loadNow(d)
        val id = d.activeSourceId!!

        d.openEntry("$id:Aaron")

        assertEquals("Aaron", d.selectedEntry?.word)
    }

    @Test
    fun `openEntry for a Strong's number goes back to Strong's from an imported dictionary`() {
        val d = vm()
        runBlocking { d.importDictionary(source("eastons.json", eastons)).join() }
        loadNow(d)

        d.openEntry("g26")
        loadNow(d)

        assertNull(d.activeSourceId)
        assertEquals("G26", d.selectedEntry?.number)
    }

    @Test
    fun `openEntry for a removed dictionary changes nothing`() {
        val d = vm()
        loadNow(d)
        awaitUntil("the library to be listed") { !d.isImporting }

        d.openEntry("dict-gone:Aaron")

        assertNull(d.activeSourceId)
        assertNull(d.selectedEntry)
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
