package org.churchpresenter.dictionary.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.File
import javax.swing.filechooser.FileNameExtensionFilter
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** One entry as a dictionary file holds it, before the library gives it a key of its own. */
data class ImportedEntry(
    /** The file's own key: a Strong's number for a lexicon, else blank or the headword. */
    val key: String,
    val headword: String,
    val definition: String,
    val transliteration: String = "",
    val pronunciation: String = "",
    val usage: String = "",
)

/** A dictionary file read: its title, its language where it says, and its entries. */
data class ImportedDictionary(val name: String, val language: String, val entries: List<ImportedEntry>)

/**
 * Reads a dictionary or lexicon file — the format chosen from the file itself:
 *
 * - **JSON**: an array of entries, or an object with `name` and `entries`. Field names are matched
 *   loosely (`word`/`term`/`headword`/`topic`, `definition`/`description`/`text`…), so the app's own
 *   Strong's files import as they are.
 * - **CSV and TSV**: a header row naming the same fields.
 * - **Zefania XML dictionaries**: `<dictionary>` of `<item id="…">` entries.
 * - **MySword, MyBible and e-Sword** SQLite modules (`.mybible`, `.dct`, `.dctx`, `.sqlite3`…) —
 *   see [SqliteDictionaryReader].
 *
 * Every definition is reduced to plain text ([DictionaryText]). An entry with neither a headword
 * nor a definition is dropped.
 */
object DictionaryImport {

    /** The import picker's filter, called [description]: every extension a dictionary file comes in. */
    fun fileFilter(description: String): FileNameExtensionFilter = FileNameExtensionFilter(
        description,
        "json", "csv", "tsv", "txt", "xml", "mybible", "dct", "dctx", "lexx", "sqlite", "sqlite3", "db",
    )

    /** The extensions [fileFilter] offers. */
    val EXTENSIONS: List<String> = fileFilter("").extensions.toList()

    private val HEADWORD_FIELDS = listOf("word", "headword", "term", "topic", "title", "lemma", "name")
    private val KEY_FIELDS = listOf("number", "strongs", "strong", "key", "id")
    private val DEFINITION_FIELDS = listOf("definition", "description", "meaning", "text", "data", "gloss")
    private val TRANSLIT_FIELDS = listOf("transliteration", "translit", "xlit")
    private val PRONUNCIATION_FIELDS = listOf("pronunciation", "pron")
    private val USAGE_FIELDS = listOf("kjvusage", "usage", "kjv")

    private const val SQLITE_MAGIC = "SQLite format 3"

    /** [file] read by the reader its contents call for. */
    fun read(file: File): ImportedDictionary {
        val fallbackName = file.name.substringBefore('.')
        val head = file.inputStream().use { it.readNBytes(SQLITE_MAGIC.length) }.toString(Charsets.ISO_8859_1)
        if (head == SQLITE_MAGIC) return SqliteDictionaryReader.read(file, fallbackName)
        val text = file.readText(Charsets.UTF_8).trimStart('\uFEFF')
        val start = text.trimStart()
        return when {
            start.startsWith("[") || start.startsWith("{") -> readJson(text, fallbackName)
            start.startsWith("<") -> readZefania(file, fallbackName)
            else -> readDelimited(text, fallbackName, if (file.extension.equals("csv", true)) ',' else null)
        }
    }

    internal fun readJson(text: String, fallbackName: String): ImportedDictionary {
        val root = Json.parseToJsonElement(text)
        val (name, language, items) = when (root) {
            is JsonArray -> Triple(fallbackName, "", root)
            is JsonObject -> Triple(
                root.string(listOf("name", "title")) ?: fallbackName,
                root.string(listOf("language", "lang")).orEmpty(),
                root["entries"] as? JsonArray ?: JsonArray(emptyList()),
            )
            else -> Triple(fallbackName, "", JsonArray(emptyList()))
        }
        val entries = items.mapNotNull { (it as? JsonObject)?.let(::entryFromJson) }
        return ImportedDictionary(name, language, entries)
    }

    private fun entryFromJson(obj: JsonObject): ImportedEntry? = entryFrom { fields -> obj.string(fields) }

    /**
     * CSV or TSV with a header row. [delimiter] is `,` for a `.csv` file; otherwise whichever of tab
     * or comma the header line holds, tab first.
     */
    internal fun readDelimited(text: String, fallbackName: String, delimiter: Char?): ImportedDictionary {
        val firstLine = text.lineSequence().firstOrNull().orEmpty()
        val separator = delimiter ?: if ('\t' in firstLine) '\t' else ','
        val rows = DelimitedText.parse(text, separator)
        val header = rows.firstOrNull()?.map { normalizeField(it) }
            ?: return ImportedDictionary(fallbackName, "", emptyList())
        val entries = rows.drop(1).mapNotNull { row ->
            entryFrom { fields ->
                fields.firstNotNullOfOrNull { field ->
                    header.indexOf(field).takeIf { it >= 0 }?.let { row.getOrNull(it) }
                }
            }
        }
        return ImportedDictionary(fallbackName, "", entries)
    }

    /** A Zefania XML dictionary: `<dictionary><INFORMATION><title>…</title></INFORMATION><item id="G1">…`. */
    internal fun readZefania(file: File, fallbackName: String): ImportedDictionary {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val root = factory.newDocumentBuilder().parse(file).documentElement
        val info = root.getElementsByTagName("INFORMATION").item(0) as? Element
        val name = info?.child("title") ?: root.getAttribute("dictionaryname").ifBlank { fallbackName }
        val language = info?.child("language").orEmpty()
        val items = root.getElementsByTagName("item")
        val entries = (0 until items.length).mapNotNull { index ->
            val item = items.item(index) as? Element ?: return@mapNotNull null
            entryFrom { fields ->
                fields.firstNotNullOfOrNull { field ->
                    if (field == "id") item.getAttribute("id").takeIf { it.isNotBlank() } else item.child(field)
                }
            }
        }
        return ImportedDictionary(name, language, entries)
    }

    /**
     * One entry from a record whose fields [lookup] reads by name — the same field names whatever
     * the format. A key that is a Strong's number is normalized (`g0026` → `G26`).
     */
    internal fun entryFrom(lookup: (List<String>) -> String?): ImportedEntry? {
        val rawKey = lookup(KEY_FIELDS)?.trim().orEmpty()
        val headword = lookup(HEADWORD_FIELDS)?.let(DictionaryText::clean).orEmpty()
        val definition = lookup(DEFINITION_FIELDS)?.let(DictionaryText::clean).orEmpty()
        if (headword.isBlank() && definition.isBlank()) return null
        val headwordNumber = normalizeStrongs(headword)
        return ImportedEntry(
            key = normalizeStrongs(rawKey) ?: headwordNumber ?: rawKey,
            // A lexicon that files each entry under its number has no headword of its own to show.
            headword = if (headwordNumber != null) "" else headword,
            definition = definition,
            transliteration = lookup(TRANSLIT_FIELDS)?.let(DictionaryText::clean).orEmpty(),
            pronunciation = lookup(PRONUNCIATION_FIELDS)?.let(DictionaryText::clean).orEmpty(),
            usage = lookup(USAGE_FIELDS)?.let(DictionaryText::clean).orEmpty(),
        )
    }

    private val STRONGS_KEY = Regex("^([HhGg])0*([1-9]\\d*)([a-z]?)$")

    /** `G0026`, `g26` or `H430` as `G26`/`H430`; null when [text] is not a Strong's number. */
    internal fun normalizeStrongs(text: String): String? =
        STRONGS_KEY.matchEntire(text.trim())?.destructured?.let { (letter, digits, variant) ->
            letter.uppercase() + digits + variant
        }
}

internal fun normalizeField(name: String): String = name.trim().trimStart('\uFEFF').lowercase().filter { it.isLetter() }

private fun JsonObject.string(names: List<String>): String? {
    val byName = entries.associate { (k, v) -> normalizeField(k) to v }
    return names.firstNotNullOfOrNull { byName[it]?.text() }?.takeIf { it.isNotBlank() }
}

private fun JsonElement.text(): String? = (this as? JsonPrimitive)?.contentOrNull

private fun Element.child(name: String): String? {
    val nodes = getElementsByTagName(name)
    return (0 until nodes.length).firstNotNullOfOrNull { index ->
        nodes.item(index)?.textContent?.trim()?.takeIf(String::isNotEmpty)
    }
}
