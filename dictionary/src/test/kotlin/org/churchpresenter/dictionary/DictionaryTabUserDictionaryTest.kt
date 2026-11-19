@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.dictionary

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.dictionary.data.UserDictionaryLibrary
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Dictionary picker in the tab: choosing, importing and removing dictionaries, what a topical
 * dictionary hides, and what a lexicon adds to a Strong's entry.
 */
class DictionaryTabUserDictionaryTest {

    private val roots = mutableListOf<File>()

    @AfterTest
    fun cleanUp() {
        roots.forEach { it.deleteRecursively() }
        roots.clear()
    }

    private fun root() = Files.createTempDirectory("cp-dictionary-tab").toFile().also { roots += it }

    private fun eastons(dir: File) = File(dir, "eastons.json").apply {
        writeText(
            """{"name":"Easton's Bible Dictionary","entries":[
            {"word":"Aaron","definition":"The eldest son of Amram."},
            {"word":"Abba","definition":"Father."}]}"""
        )
    }

    private fun thayer(dir: File) = File(dir, "thayer.tsv").apply {
        writeText("strongs\tlemma\tdefinition\nG26\tἀγάπη\taffection, good-will\n")
    }

    private fun ComposeUiTest.isShown(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.openPicker(showing: String) {
        onNodeWithText(showing).performClick()
        waitForIdle()
    }

    @Test
    fun `the picker opens on Strong's with its language filters`() {
        val library = UserDictionaryLibrary(File(root(), "dictionaries"))
        dictionaryTab(library = library) { _, _ ->
            assertTrue(isShown(STRONGS))
            assertTrue(isShown("Hebrew"), "Strong's keeps its language filters")
        }
    }

    @Test
    fun `Import asks for a file, imports it and shows its entries`() {
        val dir = root()
        val library = UserDictionaryLibrary(File(dir, "dictionaries"))
        dictionaryTab(library = library, chooseDictionaryFile = { eastons(dir) }) { vm, _ ->
            openPicker(STRONGS)
            onNodeWithText(IMPORT).performClick()
            waitUntil("the dictionary to be imported and read", TIMEOUT_MS) { vm.entries.size == 2 }
            waitForIdle()

            assertEquals("Easton's Bible Dictionary", vm.activeDictionary?.name)
            assertTrue(isShown("Aaron"))
            assertFalse(isShown("Hebrew"), "a topical dictionary has no languages to filter")
        }
    }

    @Test
    fun `cancelling the file chooser imports nothing`() {
        val library = UserDictionaryLibrary(File(root(), "dictionaries"))
        dictionaryTab(library = library, chooseDictionaryFile = { null }) { vm, _ ->
            openPicker(STRONGS)
            onNodeWithText(IMPORT).performClick()
            waitForIdle()

            assertNull(vm.activeSourceId)
            assertTrue(library.list().isEmpty())
        }
    }

    @Test
    fun `a failed import says why under the picker, and the message can be dismissed`() {
        val dir = root()
        val library = UserDictionaryLibrary(File(dir, "dictionaries"))
        val empty = File(dir, "empty.json").apply { writeText("[]") }
        dictionaryTab(library = library, chooseDictionaryFile = { empty }) { vm, _ ->
            openPicker(STRONGS)
            onNodeWithText(IMPORT).performClick()
            waitUntil("the import to fail", TIMEOUT_MS) { vm.importError != null }
            waitForIdle()

            val message = onAllNodesWithText("Could not import empty.json", substring = true)
            assertTrue(message.fetchSemanticsNodes().isNotEmpty())
            onNodeWithContentDescription("Cancel").performClick()
            waitForIdle()
            assertNull(vm.importError)
        }
    }

    @Test
    fun `choosing an imported dictionary and back again`() {
        val dir = root()
        val library = UserDictionaryLibrary(File(dir, "dictionaries")).apply { import(eastons(dir)) }
        dictionaryTab(library = library) { vm, _ ->
            waitUntil("the library to be listed", TIMEOUT_MS) { vm.userDictionaries.isNotEmpty() }
            openPicker(STRONGS)
            onNodeWithText("Easton's Bible Dictionary").performClick()
            waitUntil("Easton's to load", TIMEOUT_MS) { vm.entries.size == 2 }

            openPicker("Easton's Bible Dictionary")
            onAllNodesWithText(STRONGS).onLast().performClick()
            waitUntil("Strong's to load again", TIMEOUT_MS) {
                vm.entries.isNotEmpty() && vm.entries.all { it.isStrongsNumber }
            }
            assertNull(vm.activeSourceId)
        }
    }

    @Test
    fun `Remove asks first, then deletes the dictionary and goes back to Strong's`() {
        val dir = root()
        val library = UserDictionaryLibrary(File(dir, "dictionaries")).apply { import(eastons(dir)) }
        dictionaryTab(library = library) { vm, _ ->
            waitUntil("the library to be listed", TIMEOUT_MS) { vm.userDictionaries.isNotEmpty() }
            vm.selectSource(vm.userDictionaries.single().id)
            waitUntil("Easton's to load", TIMEOUT_MS) { vm.entries.size == 2 }
            waitForIdle()
            onNodeWithContentDescription(REMOVE).performClick()
            waitForIdle()
            assertTrue(isShown("Remove Easton's Bible Dictionary?"))
            onAllNodesWithText(CANCEL).onLast().performClick()
            waitForIdle()
            assertEquals(1, library.list().size, "Cancel removes nothing")

            onNodeWithContentDescription(REMOVE).performClick()
            waitForIdle()
            onNodeWithText("Remove").performClick()
            waitUntil("the dictionary to be removed", TIMEOUT_MS) { vm.userDictionaries.isEmpty() }

            assertNull(vm.activeSourceId)
            assertTrue(library.list().isEmpty())
        }
    }

    @Test
    fun `a Strong's entry shows what an imported lexicon says of it`() {
        val dir = root()
        val library = UserDictionaryLibrary(File(dir, "dictionaries")).apply { import(thayer(dir), name = "Thayer's") }
        dictionaryTab(library = library) { vm, _ ->
            val g26 = vm.entries.first { it.number == "G26" }
            waitUntil("the lexicons to be indexed", TIMEOUT_MS) { vm.lexiconEntriesFor(g26).isNotEmpty() }
            vm.onEntrySelected(g26)
            waitForIdle()

            assertTrue(isShown("In your lexicons"))
            assertTrue(isShown("affection, good-will"))
        }
    }

    @Test
    fun `a Strong's entry goes live carrying what each lexicon says of it`() {
        val dir = root()
        val library = UserDictionaryLibrary(File(dir, "dictionaries")).apply { import(thayer(dir), name = "Thayer's") }
        dictionaryTab(library = library) { vm, reports ->
            val g26 = vm.entries.first { it.number == "G26" }
            waitUntil("the lexicons to be indexed", TIMEOUT_MS) { vm.lexiconEntriesFor(g26).isNotEmpty() }
            vm.onEntrySelected(g26)
            waitForIdle()

            dictButton(DictionaryLabel.GO_LIVE).performClick()
            waitForIdle()

            val live = reports.live.single()
            assertEquals("G26", live.number)
            assertEquals(listOf("Thayer's"), live.companions.map { it.source })
        }
    }

    @Test
    fun `an entry with no lexicons goes live with none`() {
        val library = UserDictionaryLibrary(File(root(), "dictionaries"))
        dictionaryTab(library = library) { vm, reports ->
            vm.onEntrySelected(vm.entries.first { it.number == "H430" })
            waitForIdle()

            dictButton(DictionaryLabel.GO_LIVE).performClick()
            waitForIdle()

            assertTrue(reports.live.single().companions.isEmpty())
        }
    }

    @Test
    fun `an imported dictionary's entry shows its dictionary and never its key`() {
        val dir = root()
        val library = UserDictionaryLibrary(File(dir, "dictionaries")).apply { import(eastons(dir)) }
        dictionaryTab(library = library) { vm, _ ->
            waitUntil("the library to be listed", TIMEOUT_MS) { vm.userDictionaries.isNotEmpty() }
            vm.selectSource(vm.userDictionaries.single().id)
            waitUntil("Easton's to load", TIMEOUT_MS) { vm.entries.size == 2 }
            vm.onEntrySelected(vm.entries.first())
            waitForIdle()

            val named = onAllNodesWithText("Easton's Bible Dictionary").fetchSemanticsNodes()
            assertTrue(named.size >= 2, "picker and badge")
            assertFalse(onAllNodesWithText("dict-", substring = true).fetchSemanticsNodes().isNotEmpty())
            assertFalse(isShown("EN"), "the EN/RU switch belongs to Strong's")
        }
    }

    private companion object {
        const val STRONGS = "Strong's Concordance"
        const val IMPORT = "Import dictionary or lexicon…"
        const val REMOVE = "Remove this dictionary"
        const val CANCEL = "Cancel"
        const val TIMEOUT_MS = 5_000L
    }
}
