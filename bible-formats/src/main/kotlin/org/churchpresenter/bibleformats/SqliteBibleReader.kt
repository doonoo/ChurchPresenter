package org.churchpresenter.bibleformats

import org.churchpresenter.bible.BibleCanon
import org.sqlite.SQLiteConfig
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Reads the SQLite Bible modules MySword, e-Sword and MyBible publish, which is how many Apocrypha
 * and lost-book texts circulate:
 *
 * - **MySword** (`.bbl.mybible`) and **e-Sword** (`.bblx`): `Bible(Book, Chapter, Verse, Scripture)`,
 *   the 66 numbered as `.spb` numbers them.
 * - **MyBible** (`.SQLite3`): `verses(book_number, chapter, verse, text)`, numbered by its own codes
 *   (10 Genesis … 730 Revelation) and named in its `books` table.
 *
 * Books past the 66: MyBible names each one, so it is read against [BibleCanon] by that name. MySword
 * and e-Sword number them without naming them, and their numbering past 66 is not pinned down here,
 * so each is kept under a number from [OsisToSpbConverter.FIRST_UNLISTED_BOOK] as "Book N" -- its
 * text intact, for the operator to name in Profiles -> Book Names -- rather than guessed at.
 *
 * Verse markup -- notes, Strong's numbers, RTF -- is dropped ([ModuleMarkup.fromVerse]). Opened
 * read-only: an import never changes the file it was given.
 */
object SqliteBibleReader {

    private const val PROTESTANT_BOOKS = 66

    /** MyBible's book codes for the 66, in `.spb` order. */
    private val MYBIBLE_CODES: Map<Int, Int> = listOf(
        10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150, 160, 190, 220, 230, 240, 250, 260,
        290, 300, 310, 330, 340, 350, 360, 370, 380, 390, 400, 410, 420, 430, 440, 450, 460,
        470, 480, 490, 500, 510, 590, 600, 610, 620, 630, 640, 650, 660, 670, 680, 690, 700, 710, 720,
        520, 530, 540, 550, 560, 570, 580, 730,
    ).withIndex().associate { (index, code) -> code to index + 1 }

    /** The `.spb` number MyBible's code [code] stands for, among the 66; null for any other. */
    internal fun canonNumberForMyBible(code: Int): Int? = MYBIBLE_CODES[code]

    /** Whether [file] is a Bible module this reads: a SQLite file with either verses table. */
    fun isBibleModule(file: File): Boolean = runCatching {
        openReadOnly(file).use { db -> layoutOf(db) != null }
    }.getOrDefault(false)

    fun parse(file: File, name: String? = null): ParsedBible = openReadOnly(file).use { db ->
        val layout = layoutOf(db) ?: throw IllegalArgumentException("${file.name} has no table of Bible verses")
        val language = firstValue(db, LANGUAGE_QUERIES)?.uppercase()
        val title = name ?: firstValue(db, TITLE_QUERIES)?.let(ModuleMarkup::clean) ?: file.name.substringBefore('.')
        val books = when (layout) {
            Layout.MYSWORD -> readMySword(db, language)
            Layout.MYBIBLE -> readMyBible(db)
        }
        val rights = firstValue(db, RIGHTS_QUERIES)?.let(ModuleMarkup::clean).orEmpty()
        ParsedBible(
            name = title, description = rights, language = language, books = books, title = title, rights = rights,
        )
    }

    private enum class Layout { MYSWORD, MYBIBLE }

    private fun layoutOf(db: Connection): Layout? {
        val tables = db.metaData.getTables(null, null, "%", arrayOf("TABLE")).use { rs ->
            buildSet { while (rs.next()) add(rs.getString("TABLE_NAME").lowercase()) }
        }
        return when {
            "verses" in tables -> Layout.MYBIBLE
            "bible" in tables -> Layout.MYSWORD
            else -> null
        }
    }

    /** One verse as read: the module's own book number, then where it goes. */
    private class Row(val book: Int, val chapter: Int, val verse: Int, val text: String)

    /** Every verse [sql] selects -- book, chapter, verse, text, in that order -- with empty ones left out. */
    private fun rows(db: Connection, sql: String): List<Row> = db.createStatement().use { st ->
        st.executeQuery(sql).use { rs ->
            buildList {
                while (rs.next()) {
                    val text = ModuleMarkup.fromVerse(rs.getString(COLUMN_TEXT).orEmpty())
                    if (text.isEmpty()) continue
                    add(Row(rs.getInt(COLUMN_BOOK), rs.getInt(COLUMN_CHAPTER), rs.getInt(COLUMN_VERSE), text))
                }
            }
        }
    }

    private fun readMySword(db: Connection, language: String?): List<BibleBook> {
        val rows = rows(db, "SELECT Book, Chapter, Verse, Scripture FROM Bible ORDER BY 1, 2, 3")
        val extras = rows.map { it.book }.filter { it > PROTESTANT_BOOKS }.distinct().sorted()
        return assemble(rows) { number ->
            if (number in 1..PROTESTANT_BOOKS) {
                number to (BookNames.LANGUAGE_LOOKUPS[language]?.get(number) ?: BookNames.english(number).orEmpty())
            } else {
                OsisToSpbConverter.FIRST_UNLISTED_BOOK + extras.indexOf(number) to "Book $number"
            }
        }
    }

    private fun readMyBible(db: Connection): List<BibleBook> {
        val names = bookNames(db)
        val rows = rows(db, "SELECT book_number, chapter, verse, text FROM verses ORDER BY 1, 2, 3")
        var nextUnlisted = OsisToSpbConverter.FIRST_UNLISTED_BOOK
        val placed = mutableMapOf<Int, Pair<Int, String>>()
        return assemble(rows) { code ->
            placed.getOrPut(code) {
                val ownName = names[code]
                val number = canonNumberForMyBible(code)
                    ?: ownName?.let { BibleCanon.resolve(it)?.id }
                    ?: nextUnlisted++
                number to (ownName ?: BookNames.english(number) ?: "Book $code")
            }
        }
    }

    /** MyBible's own names for its books, by code, from `books` or the older `books_all`. */
    private fun bookNames(db: Connection): Map<Int, String> =
        listOf("books", "books_all").firstNotNullOfOrNull { table ->
            runCatching {
                db.createStatement().use { st ->
                    st.executeQuery("SELECT book_number, long_name FROM $table").use { rs ->
                        buildMap {
                            while (rs.next()) {
                                val name = rs.getString(2)?.trim().orEmpty()
                                if (name.isNotEmpty()) put(rs.getInt(1), name)
                            }
                        }
                    }
                }
            }.getOrNull()
        }.orEmpty()

    /** [rows] grouped into books by where [place] puts each module book, in `.spb` order. */
    private fun assemble(rows: List<Row>, place: (Int) -> Pair<Int, String>): List<BibleBook> =
        rows.groupBy { it.book }.map { (moduleBook, verses) ->
            val (number, name) = place(moduleBook)
            val chapters = verses.groupBy { it.chapter }.map { (chapter, inChapter) ->
                BibleChapter(chapter, inChapter.map { BibleVerse(it.verse, it.text) })
            }
            BibleBook(number, name, chapters)
        }.sortedBy { it.number }

    private val TITLE_QUERIES = listOf(
        "SELECT Title FROM Details",
        "SELECT value FROM info WHERE name = 'description'",
    )
    private val LANGUAGE_QUERIES = listOf(
        "SELECT Language FROM Details",
        "SELECT value FROM info WHERE name = 'language'",
    )
    private val RIGHTS_QUERIES = listOf(
        "SELECT Copyright FROM Details",
        "SELECT value FROM info WHERE name = 'detailed_info'",
    )

    private const val COLUMN_BOOK = 1
    private const val COLUMN_CHAPTER = 2
    private const val COLUMN_VERSE = 3
    private const val COLUMN_TEXT = 4
}

private fun openReadOnly(file: File): Connection {
    val config = SQLiteConfig().apply { setReadOnly(true) }
    return DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}", config.toProperties())
}

/** The first non-blank answer any of [queries] gives; a table or column a module lacks is skipped. */
private fun firstValue(db: Connection, queries: List<String>): String? = queries.firstNotNullOfOrNull { sql ->
    runCatching {
        db.createStatement().use { st ->
            st.executeQuery(sql).use { rs -> if (rs.next()) rs.getString(1)?.takeIf { it.isNotBlank() } else null }
        }
    }.getOrNull()
}
