package org.churchpresenter.dictionary.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.churchpresenter.core.models.dictionary.StrongsEntry
import org.churchpresenter.settings.utils.AppDataDir
import java.io.File

/** An imported dictionary as the library lists it, without its entries. */
@Serializable
data class UserDictionaryInfo(
    val id: String,
    val name: String,
    val language: String = "",
    val entryCount: Int = 0,
    /** True when its entries are keyed by Strong's numbers — a lexicon such as Thayer's or BDB. */
    val isLexicon: Boolean = false,
)

/**
 * The dictionaries and lexicons the operator has imported, kept under [dir] — by default the app
 * data folder's `dictionaries/`, next to the settings.
 *
 * An import is read once ([DictionaryImport]) and written back as the app's own JSON, so the source
 * file can be moved or deleted afterwards and every later load is one format. `index.json` lists
 * them, so the picker never reads a dictionary's entries to show its name.
 *
 * Every function here touches the disk: call them off the event thread.
 */
class UserDictionaryLibrary(private val dir: File) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val indexFile get() = File(dir, INDEX_FILE)

    /** What has been imported, in the order it was. */
    fun list(): List<UserDictionaryInfo> =
        if (!indexFile.isFile) {
            emptyList()
        } else {
            runCatching { json.decodeFromString(ListSerializer(UserDictionaryInfo.serializer()), indexFile.readText()) }
                .getOrDefault(emptyList())
        }

    /**
     * Reads [file] and adds it to the library under a new id, named [name] or else what the file
     * calls itself.
     *
     * @throws IllegalArgumentException when the file holds no entries the app can read.
     */
    fun import(file: File, name: String? = null): UserDictionaryInfo {
        val read = DictionaryImport.read(file)
        require(read.entries.isNotEmpty()) { "No dictionary entries found in ${file.name}" }
        val existing = list()
        val shownName = name?.trim()?.takeIf { it.isNotEmpty() } ?: read.name
        val id = uniqueId(shownName, existing.map { it.id }.toSet())
        val entries = toEntries(id, shownName, read.entries)
        val info = UserDictionaryInfo(
            id = id,
            name = shownName,
            language = read.language,
            entryCount = entries.size,
            isLexicon = entries.count { it.isStrongsNumber } * 2 > entries.size,
        )
        dir.mkdirs()
        File(dir, "$id.json").writeText(json.encodeToString(ListSerializer(StrongsEntry.serializer()), entries))
        writeIndex(existing + info)
        return info
    }

    /** The entries of the dictionary [id]; empty when it is not in the library or cannot be read. */
    fun load(id: String): List<StrongsEntry> {
        val file = File(dir, "$id.json")
        if (!file.isFile) return emptyList()
        return runCatching { json.decodeFromString(ListSerializer(StrongsEntry.serializer()), file.readText()) }
            .getOrDefault(emptyList())
    }

    /** Takes the dictionary [id] out of the library and deletes its copy; the file it came from is untouched. */
    fun remove(id: String) {
        val remaining = list().filterNot { it.id == id }
        File(dir, "$id.json").delete()
        writeIndex(remaining)
    }

    private fun writeIndex(infos: List<UserDictionaryInfo>) {
        dir.mkdirs()
        indexFile.writeText(json.encodeToString(ListSerializer(UserDictionaryInfo.serializer()), infos))
    }

    companion object {
        private const val INDEX_FILE = "index.json"
        private const val ID_PREFIX = "dict-"
        private const val MAX_SLUG_LENGTH = 40

        /** The library in the app data folder. Resolving that folder touches the disk. */
        fun inAppData(): UserDictionaryLibrary = UserDictionaryLibrary(File(AppDataDir.resolve(), "dictionaries"))

        /**
         * A file-safe id from [name], unlike any in [taken]. The prefix keeps every topical key
         * (`dict-…:Aaron`) from starting with the H or G a Strong's number does.
         */
        internal fun uniqueId(name: String, taken: Set<String>): String {
            val slug = name.lowercase().map { if (it.isLetterOrDigit() && it.code < 128) it else '-' }
                .joinToString("").split('-').filter { it.isNotEmpty() }.joinToString("-")
                .take(MAX_SLUG_LENGTH).ifEmpty { "dictionary" }
            val base = ID_PREFIX + slug
            return generateSequence(1) { it + 1 }.map { if (it == 1) base else "$base-$it" }.first { it !in taken }
        }

        /**
         * The file's entries as the tab and the output carry them. A Strong's-keyed entry keeps its
         * number, so it lines up with the interlinear; any other is keyed by the dictionary and its
         * headword, with a count added where a headword repeats.
         */
        internal fun toEntries(id: String, name: String, imported: List<ImportedEntry>): List<StrongsEntry> {
            val seen = mutableMapOf<String, Int>()
            return imported.map { entry ->
                val strongs = DictionaryImport.normalizeStrongs(entry.key)
                val base = strongs ?: "$id:${entry.headword.ifBlank { entry.key }}"
                val count = seen.merge(base, 1, Int::plus) ?: 1
                StrongsEntry(
                    number = if (count == 1) base else "$base#$count",
                    word = entry.headword,
                    transliteration = entry.transliteration,
                    pronunciation = entry.pronunciation,
                    definition = entry.definition,
                    kjvUsage = entry.usage,
                    source = name,
                )
            }
        }
    }
}
