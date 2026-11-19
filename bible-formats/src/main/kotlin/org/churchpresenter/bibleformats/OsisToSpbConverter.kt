package org.churchpresenter.bibleformats

import org.churchpresenter.bible.BibleCanon
import java.io.File
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader

/**
 * Reads OSIS — the XML scripture format of CrossWire's SWORD project and most digitised
 * pseudepigrapha — into a [ParsedBible], so it is written out by the same writer as Zefania and USFX.
 *
 * OSIS writes a verse two ways, and a file may mix them: as a container, `<verse osisID="Gen.1.1">text
 * </verse>`, or as milestones, `<verse sID="…" osisID="Gen.1.1"/>text<verse eID="…"/>`. Either way the
 * verse is named by its `osisID`, `Book.Chapter.Verse`, which is all this reads to place it — the
 * enclosing `<div type="book">` and `<chapter>` are only consulted for a book's title.
 *
 * Book ids are the OSIS abbreviations (`Gen`, `Tob`, `1En`, `Jub`); [OSIS_IDS] maps the standard ones
 * and [BibleCanon.resolve] catches the rest by name. A book neither knows is still kept, numbered from
 * [FIRST_UNLISTED_BOOK] in the order it appears, under its own title.
 */
object OsisToSpbConverter {

    /** Where books no table lists are numbered from: past everything [BibleCanon] reserves. */
    const val FIRST_UNLISTED_BOOK = 900

    /** Elements whose text is not scripture: notes sit inside the verse they annotate. */
    private val SKIPPED = setOf("note", "rdg", "figure")

    /** The OSIS book ids that differ from anything [BibleCanon.resolve] would read. */
    private val OSIS_IDS: Map<String, Int> = mapOf(
        "Gen" to 1, "Exod" to 2, "Lev" to 3, "Num" to 4, "Deut" to 5, "Josh" to 6, "Judg" to 7,
        "Ruth" to 8, "1Sam" to 9, "2Sam" to 10, "1Kgs" to 11, "2Kgs" to 12, "1Chr" to 13, "2Chr" to 14,
        "Ezra" to 15, "Neh" to 16, "Esth" to 17, "Job" to 18, "Ps" to 19, "Prov" to 20, "Eccl" to 21,
        "Song" to 22, "Isa" to 23, "Jer" to 24, "Lam" to 25, "Ezek" to 26, "Dan" to 27, "Hos" to 28,
        "Joel" to 29, "Amos" to 30, "Obad" to 31, "Jonah" to 32, "Mic" to 33, "Nah" to 34, "Hab" to 35,
        "Zeph" to 36, "Hag" to 37, "Zech" to 38, "Mal" to 39,
        "Matt" to 40, "Mark" to 41, "Luke" to 42, "John" to 43, "Acts" to 44, "Rom" to 45, "1Cor" to 46,
        "2Cor" to 47, "Gal" to 48, "Eph" to 49, "Phil" to 50, "Col" to 51, "1Thess" to 52,
        "2Thess" to 53, "1Tim" to 54, "2Tim" to 55, "Titus" to 56, "Phlm" to 57, "Heb" to 58, "Jas" to 59,
        "1Pet" to 60, "2Pet" to 61, "1John" to 62, "2John" to 63, "3John" to 64, "Jude" to 65, "Rev" to 66,
        "Jdt" to 67, "Wis" to 68, "Tob" to 69, "Sir" to 70, "Bar" to 71, "1Macc" to 72, "2Macc" to 73,
        "AddDan" to 74, "DanGr" to 74, "AddEsth" to 75, "EsthGr" to 75, "PrMan" to 76, "3Macc" to 77,
        "4Macc" to 78, "EpJer" to 79, "1Esd" to 80, "2Esd" to 81, "Odes" to 82, "PssSol" to 83,
        "EpLao" to 84, "PrAzar" to 85, "Sus" to 86, "Bel" to 87, "Ps151" to 88, "AddPs" to 89,
        "4Ezra" to 90, "5Ezra" to 91, "6Ezra" to 92,
        "1En" to 100, "2En" to 101, "3En" to 102, "Jub" to 103, "Jasher" to 104, "2Bar" to 113,
        "3Bar" to 114, "4Bar" to 115, "1Meq" to 123, "2Meq" to 124, "3Meq" to 125,
    )

    /** The `.spb` number of OSIS book [osisId], or null when neither table knows it. */
    internal fun bookNumberFor(osisId: String): Int? =
        OSIS_IDS[osisId.trim()] ?: BibleCanon.resolve(osisId)?.id

    fun parse(osisFile: File, name: String? = null, language: String? = null): ParsedBible {
        val factory = XMLInputFactory.newInstance().apply {
            // No external entities and no DTD: an import is a file someone downloaded.
            setProperty(XMLInputFactory.SUPPORT_DTD, false)
            setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
        }
        val reader = osisFile.inputStream().buffered().use { input ->
            val xml = factory.createXMLStreamReader(input, Charsets.UTF_8.name())
            try {
                Reader().apply { read(xml) }
            } finally {
                xml.close()
            }
        }
        val title = name ?: reader.workTitle ?: osisFile.nameWithoutExtension
        return ParsedBible(
            name = title,
            description = reader.rights,
            language = (language ?: reader.language)?.uppercase(),
            books = reader.books(),
            title = title,
            rights = reader.rights,
        )
    }

    /** One pass over the document: the work's metadata, then every verse placed by its `osisID`. */
    private class Reader {
        var workTitle: String? = null
        var language: String? = null
        var rights: String = ""

        private val bookNumbers = linkedMapOf<String, Int>()
        private val bookTitles = mutableMapOf<String, String>()
        private val verses = linkedMapOf<Int, LinkedHashMap<Int, LinkedHashMap<Int, StringBuilder>>>()
        private var nextUnlisted = FIRST_UNLISTED_BOOK

        private var currentBook: String? = null
        private var openVerse: StringBuilder? = null
        private var milestoneOpen = false
        private var skipDepth = 0
        private var headerField: String? = null
        private var bookTitleFor: String? = null
        private val pending = StringBuilder()

        fun read(xml: XMLStreamReader) {
            while (xml.hasNext()) {
                when (xml.next()) {
                    XMLStreamConstants.START_ELEMENT -> start(xml)
                    XMLStreamConstants.END_ELEMENT -> end(xml.localName)
                    XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> text(xml.text)
                }
            }
        }

        private fun start(xml: XMLStreamReader) {
            val tag = xml.localName
            if (skips(tag)) {
                skipDepth++
                return
            }
            when (tag) {
                "osisText" -> language = xml.attr("lang")?.substringBefore('-')
                "title", "rights" -> startField(tag, xml)
                "div" -> if (xml.attr("type") == "book") {
                    currentBook = xml.attr("osisID")?.substringBefore(' ')
                    bookTitleFor = currentBook
                }
                "verse" -> startVerse(xml)
            }
        }

        /** Inside a skipped element, or entering one. A title inside a verse is a heading set into the text. */
        private fun skips(tag: String): Boolean =
            skipDepth > 0 || tag in SKIPPED || (tag == "title" && openVerse != null)

        private fun startField(tag: String, xml: XMLStreamReader) {
            pending.setLength(0)
            headerField = when {
                tag == "title" && bookTitleFor != null && openVerse == null &&
                    xml.attr("type") in setOf(null, "main") -> "bookTitle"
                tag == "title" && workTitle == null && currentBook == null -> "workTitle"
                tag == "rights" -> "rights"
                else -> null
            }
        }

        private fun startVerse(xml: XMLStreamReader) {
            // A milestone's end event follows its start at once, so it must not close the verse.
            milestoneOpen = xml.attr("sID") != null || xml.attr("eID") != null
            if (xml.attr("eID") != null) {
                openVerse = null
                return
            }
            val id = xml.attr("osisID")?.substringBefore(' ') ?: xml.attr("sID") ?: return
            openVerse = verseFor(id)
        }

        private fun end(tag: String) {
            if (skipDepth > 0) {
                skipDepth--
                return
            }
            when (tag) {
                "verse" -> if (milestoneOpen) milestoneOpen = false else openVerse = null
                "title", "rights" -> endField()
            }
        }

        private fun endField() {
            val value = pending.toString().normalizeSpace()
            when (headerField) {
                "bookTitle" -> currentBook?.takeIf { value.isNotEmpty() }?.let { bookTitles.putIfAbsent(it, value) }
                "workTitle" -> workTitle = value.ifEmpty { null }
                "rights" -> rights = value
            }
            if (headerField == "bookTitle") bookTitleFor = null
            headerField = null
        }

        private fun text(value: String) {
            if (skipDepth > 0) return
            if (headerField != null) pending.append(value) else openVerse?.append(value)
        }

        /** The buffer for verse `Book.Chapter.Verse`, or null when [id] does not have that shape. */
        private fun verseFor(id: String): StringBuilder? {
            val (book, chapterText, verseText) = OSIS_VERSE_ID.find(id)?.destructured ?: return null
            val chapter = chapterText.toInt()
            val verse = verseText.toInt()
            val number = bookNumbers.getOrPut(book) { bookNumberFor(book) ?: nextUnlisted++ }
            return verses.getOrPut(number) { linkedMapOf() }
                .getOrPut(chapter) { linkedMapOf() }
                .getOrPut(verse) { StringBuilder() }
        }

        fun books(): List<BibleBook> {
            val idsByNumber = bookNumbers.entries.associate { (id, number) -> number to id }
            return verses.entries.sortedBy { it.key }.mapNotNull { (number, chapters) ->
                val built = chapters.entries.sortedBy { it.key }.mapNotNull { (chapter, byVerse) ->
                    val list = byVerse.entries.sortedBy { it.key }
                        .map { (verse, text) -> BibleVerse(verse, text.toString().normalizeSpace()) }
                        .filter { it.text.isNotEmpty() }
                    if (list.isEmpty()) null else BibleChapter(chapter, list)
                }
                if (built.isEmpty()) return@mapNotNull null
                val osisId = idsByNumber.getValue(number)
                val shown = bookTitles[osisId] ?: BookNames.english(number) ?: osisId
                BibleBook(number, shown, built)
            }
        }
    }

    /** `Book.Chapter.Verse`, with a range's end (`Gen.1.3-4`) left off. */
    private val OSIS_VERSE_ID = Regex("""^([^.]+)\.(\d+)\.(\d+)""")
}

/** An attribute's value, or null when it is missing or blank. */
private fun XMLStreamReader.attr(name: String): String? =
    getAttributeValue(null, name)?.takeIf { it.isNotBlank() }

/** XML is line-wrapped, so verse text arrives full of newlines that mean nothing. */
internal fun String.normalizeSpace(): String =
    replace('\n', ' ').replace('\r', ' ').replace('\t', ' ')
        .replace(Regex(" +"), " ")
        .trim()
