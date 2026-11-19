@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.presenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.dictionary.StrongsEntry
import org.churchpresenter.dictionary.presenter.DictionaryPresenter
import org.churchpresenter.settings.DictionarySettings
import org.churchpresenter.sharedui.screenshot.captureComponent
import kotlin.test.Test

/**
 * The dictionary card with what imported lexicons say beside Strong's, and an imported dictionary's
 * own entry, in both themes. The card is drawn on a quarter-size output so the columns are readable
 * in the image a reviewer opens.
 */
class DictionaryLexiconsScreenshotTest {

    private val agape = StrongsEntry(
        number = "G26", word = "ἀγάπη", transliteration = "agápē", pronunciation = "ag-ah'-pay",
        definition = "love, i.e. affection or benevolence; specially (plural) a love-feast",
        kjvUsage = "(feast of) charity(-ably), dear, love",
    )
    private val thayer = StrongsEntry(
        "G26", "ἀγάπη", "", "", "affection, good-will, love, benevolence: John 15:13; Rom. 13:10; 1 John 4:18.",
        source = "Thayer's",
    )
    private val abbottSmith = StrongsEntry(
        "G26", "", "", "", "the quality of warm regard for and interest in another; esteem, affection, regard, love.",
        source = "Abbott-Smith",
    )

    private fun shoot(name: String, entry: StrongsEntry) = captureComponent(SECTION, name) {
        Box(Modifier.size(960.dp, 540.dp)) {
            DictionaryPresenter(entry = entry, dictionarySettings = DictionarySettings())
        }
    }

    @Test
    fun `one lexicon beside Strong's`() = shoot("one_lexicon", agape.copy(companions = listOf(thayer)))

    @Test
    fun `two lexicons beside Strong's`() = shoot("two_lexicons", agape.copy(companions = listOf(thayer, abbottSmith)))

    @Test
    fun `an imported dictionary's entry`() = shoot(
        "imported_entry",
        StrongsEntry(
            number = "dict-eastons:Abba", word = "Abba", transliteration = "", pronunciation = "",
            definition = "This Syriac or Chaldee word is found three times in the New Testament (Mark 14:36; " +
                "Rom. 8:15; Gal. 4:6), and in each case is followed by its Greek equivalent, translated father.",
            source = "Easton's Bible Dictionary",
        ),
    )

    private companion object {
        const val SECTION = "dictionaryCard"
    }
}
