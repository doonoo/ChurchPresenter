package org.churchpresenter.app.churchpresenter

import androidx.compose.runtime.mutableStateOf
import org.churchpresenter.core.models.dictionary.StrongsEntry
import org.churchpresenter.core.models.schedule.dictionaryItemFor
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.models.Presenting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A dictionary row played from the schedule: on the dictionary card, named by its dictionary, with
 * the imported lexicons beside a Strong's entry -- as the Dictionary tab's Go Live puts it there.
 */
class ScheduleDictionaryRowTest {

    private val thayer = StrongsEntry("G26", "ἀγάπη", "", "", "affection, good-will", source = "Thayer's")

    private fun play(entry: StrongsEntry): Pair<PresenterManager, List<Presenting>> {
        val presenterManager = PresenterManager()
        val modes = mutableListOf<Presenting>()
        val rows = ScheduleRowHandlers(
            state = MainDesktopState(mutableStateOf(0), showCrashFeedbackInitially = false),
            selectTab = {},
            presenting = { modes += it },
            presenterManager = presenterManager,
            onSettingsChange = { _: (AppSettings) -> AppSettings -> },
            lowerThirdFolder = { "" },
            lexiconsFor = { if (it.number == "G26" && it.source.isBlank()) listOf(thayer) else emptyList() },
        )
        rows.presentDictionary(dictionaryItemFor("row", entry))
        return presenterManager to modes
    }

    @Test
    fun `a Strong's row goes on the dictionary card with its lexicons beside it`() {
        val (presenterManager, modes) = play(StrongsEntry("G26", "ἀγάπη", "agápē", "ag-ah'-pay", "love"))

        assertEquals(listOf(Presenting.DICTIONARY), modes)
        val shown = presenterManager.displayedDictionaryEntry.value!!
        assertEquals("G26", shown.number)
        assertEquals("ag-ah'-pay", shown.pronunciation)
        assertEquals(listOf(thayer), shown.companions)
        assertTrue(presenterManager.showPresenterWindow.value)
    }

    @Test
    fun `an imported dictionary's row keeps its dictionary's name`() {
        val (presenterManager, _) = play(
            StrongsEntry("dict-eastons:Aaron", "Aaron", "", "", "The eldest son of Amram.", source = "Easton's"),
        )

        val shown = presenterManager.displayedDictionaryEntry.value!!
        assertEquals("Easton's", shown.shownReference)
        assertTrue(shown.companions.isEmpty())
    }
}
