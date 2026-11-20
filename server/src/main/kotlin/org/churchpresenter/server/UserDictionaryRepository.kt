package org.churchpresenter.server

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import org.churchpresenter.core.models.dictionary.StrongsEntry
import org.churchpresenter.dictionary.data.UserDictionaryInfo
import org.churchpresenter.dictionary.data.UserDictionaryLibrary

/** One imported dictionary or lexicon, as `GET /api/dictionaries` lists it. */
@Serializable
data class UserDictionaryDto(
    val id: String,
    val name: String,
    val language: String,
    val entryCount: Int,
    /** True for a lexicon keyed by Strong's numbers (Thayer's, BDB), false for a topical dictionary (Easton's). */
    val isLexicon: Boolean,
)

/**
 * One entry of an imported dictionary, for a phone.
 *
 * @property key what the entry is filed under: a Strong's number for a lexicon, else
 *   `<dictionary id>:<headword>`. A phone sends it back as `strongsNumber` to show or schedule it.
 * @property reference what the output's reference line says -- the dictionary's name, with the
 *   Strong's number for a lexicon -- never [key].
 */
@Serializable
data class UserDictionaryEntryDto(
    val key: String,
    val word: String,
    val transliteration: String,
    val pronunciation: String,
    val definition: String,
    val kjvUsage: String,
    val source: String,
    val reference: String,
)

internal fun StrongsEntry.toUserDictionaryDto() = UserDictionaryEntryDto(
    key = number, word = word, transliteration = transliteration, pronunciation = pronunciation,
    definition = definition, kjvUsage = kjvUsage, source = source, reference = shownReference,
)

private fun UserDictionaryInfo.toDto() = UserDictionaryDto(id, name, language, entryCount, isLexicon)

/**
 * The dictionaries the operator imported in the Dictionary tab, served to phones: the list, a search
 * through one, and what each lexicon says of a Strong's number.
 *
 * Reads the same library the tab writes ([library]), on whatever thread the route runs -- Ktor's, never
 * the event thread. A dictionary's entries are read once and kept until the library's list says it
 * changed, so a phone typing a search does not re-read the file per letter.
 */
class UserDictionaryRepository(library: () -> UserDictionaryLibrary) {

    private val library by lazy(library)
    private val mutex = Mutex()
    private val cache = mutableMapOf<String, Pair<UserDictionaryInfo, List<StrongsEntry>>>()

    suspend fun list(): List<UserDictionaryDto> = library.list().map { it.toDto() }

    /**
     * Entries of the dictionary [id] matching [query] in their headword, key, transliteration or
     * definition, at most [limit]; null when there is no such dictionary.
     */
    suspend fun search(id: String, query: String, limit: Int): List<UserDictionaryEntryDto>? {
        val info = library.list().firstOrNull { it.id == id } ?: return null
        val q = query.trim().lowercase()
        return entriesOf(info)
            .asSequence()
            .filter { q.isEmpty() || it.matches(q) }
            .take(limit.coerceAtLeast(0))
            .map { it.toUserDictionaryDto() }
            .toList()
    }

    /** What each imported lexicon says of the Strong's number [number], in import order. */
    suspend fun lexicons(number: String): List<UserDictionaryEntryDto> {
        val target = number.trim().uppercase()
        return library.list().filter { it.isLexicon }
            .flatMap { info -> entriesOf(info).filter { it.number.uppercase() == target } }
            .map { it.toUserDictionaryDto() }
    }

    private fun StrongsEntry.matches(q: String): Boolean =
        word.lowercase().contains(q) || number.lowercase().contains(q) ||
            transliteration.lowercase().contains(q) || definition.lowercase().contains(q)

    private suspend fun entriesOf(info: UserDictionaryInfo): List<StrongsEntry> = mutex.withLock {
        cache[info.id]?.takeIf { it.first == info }?.second
            ?: library.load(info.id).also { cache[info.id] = info to it }
    }
}
