package org.churchpresenter.bibleformats

import java.io.File
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants

/**
 * Reads any scripture file the app can import — Zefania, Beblia, OSIS, USFX or plain text — choosing
 * the reader from the file itself, so an operator importing a lost book need not know its format.
 *
 * XML is told apart by its root element, read without parsing the rest of the file. A file that is
 * not XML and not named as XML is read as plain text ([PlainTextBookParser]), with the file's name as
 * the book's. A broken `.xml` file stays XML, so its parse error is reported rather than the file
 * quietly becoming an empty plain-text module.
 */
object BibleImport {

    /** What a file was recognised as. */
    enum class Format { ZEFANIA, OSIS, USFX, PLAIN_TEXT }

    /** The extensions the import picker offers. */
    val EXTENSIONS: List<String> = listOf("xml", "osis", "usfx", "txt")

    /** The extensions that always mean XML, whether or not the file inside parses. */
    private val XML_EXTENSIONS = setOf("xml", "osis", "usfx")

    /** Which reader [file] needs: by its XML root, or [Format.PLAIN_TEXT] for a text file. */
    fun detect(file: File): Format {
        val root = rootElementOf(file)?.lowercase()
        return when {
            root == null && file.extension.lowercase() !in XML_EXTENSIONS -> Format.PLAIN_TEXT
            root == "osis" -> Format.OSIS
            root == "usfx" -> Format.USFX
            // XMLBIBLE (Zefania), Beblia's <bible> -- XmlToSpbConverter tells those apart itself --
            // and a broken XML file, whose parse error that reader reports.
            else -> Format.ZEFANIA
        }
    }

    /** [file] read by the reader [detect] picks. */
    fun parse(file: File): ParsedBible = when (detect(file)) {
        Format.ZEFANIA -> XmlToSpbConverter.parse(file)
        Format.OSIS -> OsisToSpbConverter.parse(file)
        Format.USFX -> UsfxToSpbConverter.parse(
            usfxFile = file,
            bookNamesFile = File(file.parentFile, "BookNames.xml"),
            name = file.nameWithoutExtension,
            language = null,
        )
        Format.PLAIN_TEXT -> PlainTextBookParser.parse(file, bookName = file.nameWithoutExtension).also {
            require(it.hasVerses) { "No numbered verses found in ${file.name}" }
        }
    }

    /** [file] read and written to [outputFile] as an `.spb` module. */
    fun convert(file: File, outputFile: File) = XmlToSpbConverter.write(parse(file), outputFile)

    /** The local name of [file]'s root element, or null when the file does not open as XML. */
    internal fun rootElementOf(file: File): String? {
        val factory = XMLInputFactory.newInstance().apply {
            setProperty(XMLInputFactory.SUPPORT_DTD, false)
            setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
        }
        return file.inputStream().buffered().use { input ->
            runCatching {
                val xml = factory.createXMLStreamReader(input)
                try {
                    // Past the prolog to the first element; a file with none throws at its end.
                    do {
                        val event = xml.next()
                    } while (event != XMLStreamConstants.START_ELEMENT)
                    xml.localName
                } finally {
                    xml.close()
                }
            }.getOrNull()
        }
    }
}
