package org.churchpresenter.dictionary.data

import org.sqlite.SQLiteConfig
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Reads the SQLite dictionary modules most free Bible dictionaries and lexicons are published as:
 *
 * | program | entries table | its columns | title from |
 * |---|---|---|---|
 * | MySword (`.dct.mybible`) | `dictionary` | `word`, `data` (HTML) | `details.title` |
 * | MyBible (`.dictionary.SQLite3`) | `dictionary` | `topic`, `definition` (HTML) | `info` row `description` |
 * | e-Sword (`.dctx`, `.lexx`) | `Dictionary` or `Lexicon` | `Topic`, `Definition` (RTF) | `Details.Title` |
 *
 * Rather than one branch per program, it takes the first table that has both a headword column and
 * a definition column by any of those names, which also reads the variants modules drift into.
 * Opened read-only: an import never changes the file it was given.
 */
internal object SqliteDictionaryReader {

    private val HEADWORD_COLUMNS = listOf("word", "topic", "term", "headword", "lemma")
    private val DEFINITION_COLUMNS = listOf("data", "definition", "description", "meaning")
    private val TITLE_QUERIES = listOf(
        "SELECT title FROM details",
        "SELECT value FROM info WHERE name IN ('description', 'title')",
    )
    private val LANGUAGE_QUERIES = listOf(
        "SELECT language FROM details",
        "SELECT value FROM info WHERE name = 'language'",
    )

    fun read(file: File, fallbackName: String): ImportedDictionary {
        val config = SQLiteConfig().apply { setReadOnly(true) }
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}", config.toProperties()).use { db ->
            val (table, headword, definition) = entriesTable(db)
                ?: throw IllegalArgumentException("${file.name} has no table of dictionary entries")
            val entries = readEntries(db, table, headword, definition)
            val name = firstValue(db, TITLE_QUERIES)?.let(DictionaryText::clean) ?: fallbackName
            return ImportedDictionary(name, firstValue(db, LANGUAGE_QUERIES).orEmpty(), entries)
        }
    }

    /** Every row of [table] as an entry: [headword] is its word and key, [definition] its text. */
    private fun readEntries(db: Connection, table: String, headword: String, definition: String): List<ImportedEntry> {
        val entries = mutableListOf<ImportedEntry>()
        db.createStatement().use { st ->
            st.executeQuery("SELECT \"$headword\", \"$definition\" FROM \"$table\"").use { rows ->
                while (rows.next()) {
                    val word = rows.getString(1).orEmpty()
                    val text = rows.getString(2).orEmpty()
                    DictionaryImport.entryFrom { fields -> fieldOf(fields, word, text) }?.let(entries::add)
                }
            }
        }
        return entries
    }

    /** What a two-column row answers for the field names [fields]: its word, its text, or nothing. */
    private fun fieldOf(fields: List<String>, word: String, text: String): String? = when {
        "word" in fields || "key" in fields -> word
        "definition" in fields -> text
        else -> null
    }

    /** The first table with a headword column and a definition column, and those columns' names. */
    private fun entriesTable(db: Connection): Triple<String, String, String>? {
        val tables = db.metaData.getTables(null, null, "%", arrayOf("TABLE")).use { rs ->
            buildList { while (rs.next()) add(rs.getString("TABLE_NAME")) }
        }
        return tables.firstNotNullOfOrNull { table ->
            val columns = db.metaData.getColumns(null, null, table, "%").use { rs ->
                buildList { while (rs.next()) add(rs.getString("COLUMN_NAME")) }
            }
            val headword = columns.firstOrNull { it.lowercase() in HEADWORD_COLUMNS }
            val definition = columns.firstOrNull { it.lowercase() in DEFINITION_COLUMNS }
            if (headword != null && definition != null) Triple(table, headword, definition) else null
        }
    }

    /** The first non-blank answer any of [queries] gives; a table a module does not have is skipped. */
    private fun firstValue(db: Connection, queries: List<String>): String? = queries.firstNotNullOfOrNull { sql ->
        runCatching {
            db.createStatement().use { st ->
                st.executeQuery(sql).use { rs -> if (rs.next()) rs.getString(1)?.takeIf { it.isNotBlank() } else null }
            }
        }.getOrNull()
    }
}
