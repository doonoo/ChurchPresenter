package org.churchpresenter.core.models.dictionary

import kotlinx.serialization.Serializable

/** `H430`, `G26`, `H1254a`: a letter for the alphabet, then the number, then sometimes a variant letter. */
private val STRONGS_NUMBER = Regex("^[HG][1-9]\\d*[a-z]?$")

/**
 * One dictionary or lexicon entry: a Strong's entry, or one from a dictionary the operator imported.
 *
 * @property number the entry's key — a Strong's number (`H1`, `G26`), or for a topical dictionary's
 *   entry `<dictionary id>:<headword>`, which nothing shows.
 * @property source the imported dictionary's name, blank for the built-in Strong's. Shown on the
 *   output in place of a key that means nothing to the congregation.
 */
@Serializable
data class StrongsEntry(
    val number: String,
    val word: String,
    val transliteration: String,
    val pronunciation: String,
    val definition: String,
    val kjvUsage: String = "",
    val source: String = "",
) {
    val isHebrew: Boolean get() = number.startsWith("H")
    val isGreek: Boolean get() = number.startsWith("G")
    val numericValue: Int get() = number.drop(1).toIntOrNull() ?: 0

    /** Whether [number] is a Strong's number, so the entry has a place in the interlinear. */
    val isStrongsNumber: Boolean get() = STRONGS_NUMBER.matches(number)

    /**
     * What the output's reference line says: the Strong's number, the dictionary's name, or both
     * for a lexicon keyed by Strong's numbers.
     */
    val shownReference: String
        get() = when {
            source.isBlank() -> number
            isStrongsNumber -> "$number · $source"
            else -> source
        }
}
