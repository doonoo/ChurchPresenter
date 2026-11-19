@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.bibletab

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import org.churchpresenter.bible.CanonSection
import org.churchpresenter.bible.SpbFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A module carrying the Apocrypha and the lost books, in the Bible tab: every book listed, each
 * section headed, and the extra books reachable by their English names as well as their own.
 */
class BibleTabLostBooksTest {

    private fun ComposeUiTest.isShown(text: String, substring: Boolean = false) =
        onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `every book is listed and each extra section is headed`() = bibleTab(content = lostBooksFixture) { vm, _ ->
        assertEquals(13, vm.books.value.size)
        assertEquals("Didache", vm.books.value.last())
        listOf("Old Testament", "New Testament", "Apocrypha", "Lost Books", "New Testament Apocrypha")
            .forEach { assertTrue(isShown(it), "the \"$it\" heading must be shown") }
    }

    @Test
    fun `each book's section runs parallel to the book list`() = bibleTab(content = lostBooksFixture) { vm, _ ->
        val sections = vm.bookSections.value
        assertEquals(vm.books.value.size, sections.size)
        assertEquals(CanonSection.OLD_TESTAMENT, sections.first())
        assertEquals(CanonSection.DEUTEROCANON, sections[vm.books.value.indexOf("Tobit")])
        assertEquals(CanonSection.OT_PSEUDEPIGRAPHA, sections[vm.books.value.indexOf("Jasher")])
        assertEquals(CanonSection.NT_APOCRYPHA, sections.last())
    }

    @Test
    fun `an ordinary Bible shows no section headings`() = bibleTab { _, _ ->
        assertFalse(isShown("Old Testament"))
        assertFalse(isShown("Apocrypha"))
    }

    @Test
    fun `a module with more than sixty-six books lists all of them`() {
        val books = (1..66).map { SpbFixture.Book(it, "Book $it name", 1) } + SpbFixture.Book(69, "Tobit", 1)
        val content = SpbFixture.buildContent(
            title = "Catholic order",
            books = books,
            verses = listOf(SpbFixture.Verse(1, 1, 1, "In the beginning."), SpbFixture.Verse(69, 1, 1, "Tobit.")),
        )
        bibleTab(content = content) { vm, _ ->
            assertEquals(67, vm.books.value.size, "nothing past the 66th book may be cut off")
            assertEquals("Tobit", vm.books.value.last())
        }
    }

    @Test
    fun `an extra book is found by its English name though the module names it otherwise`() =
        bibleTab(content = lostBooksFixture) { vm, _ ->
            vm.updateBookSearchQuery("enoch")
            waitForIdle()

            assertEquals(listOf("Книга Еноха"), vm.filteredBooks.value)
        }

    @Test
    fun `a verse in an extra book is selected by its canon number`() = bibleTab(content = lostBooksFixture) { vm, _ ->
        assertTrue(vm.selectVerseByCanonicalRef(100, 1, 2))
        waitForIdle()

        assertEquals(vm.books.value.indexOf("Книга Еноха"), vm.selectedBookIndex.value)
        assertTrue(isShown("And he took up his parable", substring = true))
    }
}
