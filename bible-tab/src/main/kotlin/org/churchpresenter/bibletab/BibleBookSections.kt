package org.churchpresenter.bibletab

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.churchpresenter.bible.CanonSection
import org.churchpresenter.sharedui.composables.rowPad
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.bible_section_deuterocanon
import org.churchpresenter.strings.generated.resources.bible_section_new_testament
import org.churchpresenter.strings.generated.resources.bible_section_nt_apocrypha
import org.churchpresenter.strings.generated.resources.bible_section_old_testament
import org.churchpresenter.strings.generated.resources.bible_section_ot_pseudepigrapha
import org.churchpresenter.strings.generated.resources.bible_section_other
import org.jetbrains.compose.resources.stringResource

/**
 * The section of each book in the tab's book list, by position — see `BibleViewModel.bookSections`.
 *
 * Provided rather than passed, like [LocalVerseSplitMark]: the book column that reads it is several
 * levels below the tab, through a pane that already carries over forty parameters.
 */
internal val LocalBookSections = compositionLocalOf<List<CanonSection?>> { emptyList() }

/**
 * Where the book column puts a section heading: each position in [filteredBooks] whose book starts a
 * section, with that section (null for books [org.churchpresenter.bible.BibleCanon] does not list).
 *
 * Empty when every book is one of the 66, so an ordinary Bible's list is exactly as it always was;
 * the headings only appear once a module carries the Apocrypha or any other extra book.
 *
 * @param books the whole book list, which [sections] runs parallel to.
 * @param filteredBooks what the column shows — [books], or the subset a typed filter left.
 */
internal fun bookSectionHeaders(
    books: List<String>,
    filteredBooks: List<String>,
    sections: List<CanonSection?>,
): Map<Int, CanonSection?> {
    if (sections.all { it?.isProtestantCanon == true }) return emptyMap()
    val headers = linkedMapOf<Int, CanonSection?>()
    filteredBooks.forEachIndexed { index, name ->
        val section = sections.getOrNull(books.indexOf(name))
        val previous = filteredBooks.getOrNull(index - 1)?.let { sections.getOrNull(books.indexOf(it)) }
        if (index == 0 || section != previous) headers[index] = section
    }
    return headers
}

/** The heading the book column shows above the first book of each section. */
@Composable
internal fun bookSectionLabels(): (CanonSection?) -> String {
    val old = stringResource(Res.string.bible_section_old_testament)
    val new = stringResource(Res.string.bible_section_new_testament)
    val deutero = stringResource(Res.string.bible_section_deuterocanon)
    val pseudepigrapha = stringResource(Res.string.bible_section_ot_pseudepigrapha)
    val ntApocrypha = stringResource(Res.string.bible_section_nt_apocrypha)
    val other = stringResource(Res.string.bible_section_other)
    return { section ->
        when (section) {
            CanonSection.OLD_TESTAMENT -> old
            CanonSection.NEW_TESTAMENT -> new
            CanonSection.DEUTEROCANON -> deutero
            CanonSection.OT_PSEUDEPIGRAPHA -> pseudepigrapha
            CanonSection.NT_APOCRYPHA -> ntApocrypha
            null -> other
        }
    }
}

/** A section's name above its first row: small, quiet, and not a thing to click. */
@Composable
internal fun BibleSectionHeading(text: String, first: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth()
            .semantics { heading() }
            .padding(start = rowPad(10.dp), top = if (first) 2.dp else 10.dp, bottom = 2.dp),
    )
}
