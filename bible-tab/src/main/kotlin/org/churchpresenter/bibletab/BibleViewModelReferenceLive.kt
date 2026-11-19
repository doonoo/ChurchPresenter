package org.churchpresenter.bibletab

import org.churchpresenter.bible.BibleCanon

/**
 * Puts the reference [text] — "John 3:16-18", "Иоанна 3:16", "jn 3 16" — on screen as the search box
 * and Go Live do: the book read against this Bible's own names and the standard English ones, then
 * shown the tab's way, with every Bible setting. False when [text] names no book this Bible has.
 */
fun BibleViewModel.goLiveWithReference(text: String): Boolean {
    val ref = parseReference(text.trim())?.takeIf { it.chapter != null } ?: return false
    navigateToReference(ref, goLive = true, goLiveSource = "helper")
    return true
}

/**
 * The standard English name of the book [bookId], as the search box also reads it — the 66 and every
 * extra book [BibleCanon] lists; null for a number it does not.
 */
fun standardEnglishBookName(bookId: Int): String? = BibleCanon.englishName(bookId)?.lowercase()
