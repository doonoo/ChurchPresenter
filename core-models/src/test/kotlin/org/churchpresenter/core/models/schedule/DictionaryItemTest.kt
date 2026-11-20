package org.churchpresenter.core.models.schedule

import kotlinx.serialization.json.Json
import org.churchpresenter.core.models.dictionary.StrongsEntry
import kotlin.test.Test
import kotlin.test.assertEquals

/** A dictionary row: what it is labelled, the entry it puts on screen, and the files it reads. */
class DictionaryItemTest {

    private val lenient = Json { ignoreUnknownKeys = true }

    @Test
    fun `a Strong's row is labelled by its number and an imported one by its dictionary, never its key`() {
        assertEquals("ἀγάπη (G26)", dictionaryRowLabel("ἀγάπη", "G26", ""))
        assertEquals("Aaron (Easton's)", dictionaryRowLabel("Aaron", "dict-eastons:Aaron", "Easton's"))
        assertEquals("ἀγάπη (G26 · Thayer's)", dictionaryRowLabel("ἀγάπη", "G26", "Thayer's"))
        assertEquals("G26 · Thayer's", dictionaryRowLabel("", "G26", "Thayer's"), "a lexicon entry with no headword")
    }

    @Test
    fun `a row made from an entry gives the same entry back`() {
        val entry = StrongsEntry("G26", "ἀγάπη", "agápē", "ag-ah'-pay", "love", "charity", source = "Thayer's")
        val item = dictionaryItemFor("row-1", entry)

        assertEquals("row-1", item.id)
        assertEquals(entry, item.entry)
        assertEquals("ἀγάπη (G26 · Thayer's)", item.displayText)
    }

    @Test
    fun `a row's lexicons are not saved with it`() {
        val lexicon = StrongsEntry("G26", "", "", "", "x")
        val entry = StrongsEntry("G26", "ἀγάπη", "", "", "love", companions = listOf(lexicon))

        assertEquals(emptyList(), dictionaryItemFor("r", entry).entry.companions)
    }

    @Test
    fun `a row saved before the dictionary's name was kept still reads, as a Strong's row`() {
        val saved = """{"type":"org.churchpresenter.app.churchpresenter.models.ScheduleItem.DictionaryItem",
            "id":"d1","number":"H430","word":"Elohim","transliteration":"el-o-heem","definition":"God",
            "displayText":"Elohim (H430)"}"""

        val item = lenient.decodeFromString(ScheduleItem.serializer(), saved) as ScheduleItem.DictionaryItem

        assertEquals("", item.source)
        assertEquals("", item.pronunciation)
        assertEquals("Elohim (H430)", item.displayText)
        assertEquals("H430", item.entry.shownReference)
    }
}
