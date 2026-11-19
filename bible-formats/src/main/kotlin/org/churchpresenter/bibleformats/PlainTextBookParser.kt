package org.churchpresenter.bibleformats

import org.churchpresenter.bible.BibleCanon
import java.io.File

/**
 * Reads a book typed out as plain text — how Enoch, Jasher, Jubilees and most other lost books are
 * found online — into a [ParsedBible].
 *
 * It reads one line at a time, and understands what such files actually contain:
 *
 * - **a book heading**: `# 1 Enoch`, `Book: Jubilees` or `BOOK OF JASHER`. The name is read against
 *   [BibleCanon], so a known book keeps its number; an unknown one is numbered from
 *   [OsisToSpbConverter.FIRST_UNLISTED_BOOK] and keeps its own name. A file with no heading is one
 *   book, named by the caller.
 * - **a chapter heading**: `Chapter 3`, `CHAPTER XII`, `Ch. 4` — arabic or roman.
 * - **a verse**: `3 And it came to pass…`, `3. And…`, `[3] And…`, or a full `4:3 And…` that names its
 *   own chapter.
 * - **anything else**: carried on to the verse before it, since printed verses wrap. Text before the
 *   first verse number — a preface, a translator's note — is dropped rather than made into verse 1.
 */
object PlainTextBookParser {

    private val BOOK_HEADING = Regex("""^(?:#+\s*|book\s*:\s*)(.+)$""", RegexOption.IGNORE_CASE)
    private val BOOK_OF_HEADING = Regex("""^(?:the\s+)?book\s+of\s+(.+)$""", RegexOption.IGNORE_CASE)
    private val CHAPTER_HEADING =
        Regex("""^(?:chapter|chap\.?|ch\.?)\s+([0-9]+|[ivxlcdm]+)\b.*$""", RegexOption.IGNORE_CASE)
    private val FULL_REFERENCE = Regex("""^(\d+)\s*:\s*(\d+)[.)]?\s+(.+)$""")
    private val VERSE = Regex("""^\[?(\d+)[\].)]?\s+(.+)$""")

    /**
     * @param bookName the book a file with no heading of its own is; ignored when the file names its books.
     * @param name the module's title, shown in the translation picker.
     */
    fun parse(file: File, bookName: String, name: String = bookName, language: String? = null): ParsedBible =
        parse(file.readLines(Charsets.UTF_8), bookName, name, language)

    fun parse(lines: List<String>, bookName: String, name: String = bookName, language: String? = null): ParsedBible {
        val collector = Collector(bookName)
        lines.map { it.trim().trimStart('\uFEFF') }.filter { it.isNotEmpty() }.forEach { collector.line(it) }
        return ParsedBible(
            name = name,
            description = "",
            language = language?.uppercase(),
            books = collector.finish(),
            title = name,
        )
    }

    /** Roman numerals to the end of a chapter heading: `XII` → 12; null for a malformed numeral. */
    internal fun romanToInt(text: String): Int? {
        val values = mapOf('i' to 1, 'v' to 5, 'x' to 10, 'l' to 50, 'c' to 100, 'd' to 500, 'm' to 1000)
        val digits = text.lowercase().map { values[it] ?: return null }
        return digits.indices.sumOf { i ->
            if (i + 1 < digits.size && digits[i] < digits[i + 1]) -digits[i] else digits[i]
        }.takeIf { it > 0 }
    }

    /**
     * Gathers verses into books. A book is only started when something is read into it — a heading,
     * or a verse with no heading before it, which starts [defaultName] — so a file that opens with its
     * own heading never leaves an empty book under the caller's name.
     */
    private class Collector(private val defaultName: String) {
        private val books = linkedMapOf<Int, Pair<String, LinkedHashMap<Int, LinkedHashMap<Int, StringBuilder>>>>()
        private var nextUnlisted = OsisToSpbConverter.FIRST_UNLISTED_BOOK
        private var chapters: LinkedHashMap<Int, LinkedHashMap<Int, StringBuilder>>? = null
        private var chapter = 1
        private var verse: StringBuilder? = null

        /** A heading's book: its [BibleCanon] number and English name when it is one the app knows. */
        fun startBook(heading: String) {
            val canon = BibleCanon.resolve(heading.trim())
            open(canon?.id, canon?.englishName ?: heading.trim())
        }

        private fun open(knownNumber: Int?, name: String): LinkedHashMap<Int, LinkedHashMap<Int, StringBuilder>> {
            val number = knownNumber
                ?: books.entries.firstOrNull { it.value.first == name }?.key
                ?: nextUnlisted++
            val opened = books.getOrPut(number) { name to linkedMapOf() }.second
            chapters = opened
            chapter = 1
            verse = null
            return opened
        }

        fun line(line: String) {
            if (!bookHeading(line) && !chapterHeading(line) && !verseLine(line)) verse?.append(' ')?.append(line)
        }

        /** A book heading: `# Name`, `Book: Name`, or `Book of Name` — which mid-verse must name a known book. */
        private fun bookHeading(line: String): Boolean {
            val name = BOOK_HEADING.matchEntire(line)?.groupValues?.get(1)
                ?: BOOK_OF_HEADING.matchEntire(line)?.groupValues?.get(1)
                    ?.takeIf { verse == null || BibleCanon.resolve(it) != null }
                ?: return false
            startBook(name)
            return true
        }

        private fun chapterHeading(line: String): Boolean {
            val token = CHAPTER_HEADING.matchEntire(line)?.groupValues?.get(1) ?: return false
            val number = token.toIntOrNull() ?: romanToInt(token) ?: return false
            chapter = number
            verse = null
            return true
        }

        private fun verseLine(line: String): Boolean {
            FULL_REFERENCE.matchEntire(line)?.destructured?.let { (chapterText, number, text) ->
                chapter = chapterText.toInt()
                startVerse(number.toInt(), text)
                return true
            }
            val (number, text) = VERSE.matchEntire(line)?.destructured ?: return false
            startVerse(number.toInt(), text)
            return true
        }

        private fun startVerse(number: Int, text: String) {
            // Opening the default book here must keep a chapter heading read before the first verse.
            val into = chapters ?: chapter.let { heading ->
                open(BibleCanon.resolve(defaultName)?.id, defaultName.trim()).also { chapter = heading }
            }
            verse = into.getOrPut(chapter) { linkedMapOf() }.getOrPut(number) { StringBuilder() }.also {
                if (it.isNotEmpty()) it.append(' ')
                it.append(text)
            }
        }

        fun finish(): List<BibleBook> = books.entries.sortedBy { it.key }.mapNotNull { (number, entry) ->
            val (name, byChapter) = entry
            val built = byChapter.entries.sortedBy { it.key }.map { (chapterNumber, verses) ->
                BibleChapter(
                    chapterNumber,
                    verses.entries.sortedBy { it.key }
                        .map { (n, text) -> BibleVerse(n, text.toString().normalizeSpace()) },
                )
            }.filter { it.verses.isNotEmpty() }
            if (built.isEmpty()) null else BibleBook(number, name, built)
        }
    }
}
