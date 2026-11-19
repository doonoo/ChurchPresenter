package org.churchpresenter.bibleformats

import java.io.File
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants

/**
 * Reads any scripture file the app can import — Zefania, Beblia, OSIS, USFX or plain text — choosing
 * the reader from the file itself, so an operator importing a lost book need not know its format.
 *
 * XML is told apart by its root element, read without parsing the rest of the file. Anything that is
 * not XML is read as plain text ([PlainTextBookParser]), with the file's name as the book's.
 */
object BibleImport {

    /** What a file was recognised as. */
    enum class Format { ZEFANIA, OSIS, USFX, PLAIN_TEXT }

    /** The extensions the import picker offers. */
    val EXTENSIONS: List<String> = listOf("xml", "osis", "usfx", "txt")

    /** Which reader [file] needs: by its XML root, or [Format.PLAIN_TEXT] when it is not XML at all. */
    fun detect(file: File): Format = when (rootElementOf(file)?.lowercase()) {
        null -> Format.PLAIN_TEXT
        "osis" -> Format.OSIS
        "usfx" -> Format.USFX
        // XMLBIBLE (Zefania), and Beblia's <bible>, which XmlToSpbConverter tells apart itself.
        else -> Format.ZEFANIA
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
        Format.PLAIN_TEXT -> PlainTextBookParser.parse(file, bookName = file.nameWithoutExtension)
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
                    while (xml.hasNext()) {
                        if (xml.next() == XMLStreamConstants.START_ELEMENT) return@runCatching xml.localName
                    }
                    null
                } finally {
                    xml.close()
                }
            }.getOrNull()
        }
    }
}
