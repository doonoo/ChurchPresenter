package org.churchpresenter.bibleformats

/**
 * Turns the markup Bible software writes its modules in into the plain text the app shows: MySword
 * and MyBible write HTML (and, in Bible verses, their own tags -- see [fromVerse]), e-Sword writes
 * RTF. Shared by the Bible importer here and the dictionary importer in `:dictionary`.
 */
object ModuleMarkup {

    private val BREAK_TAG = Regex("(?i)<\\s*(br|/p|/div|/li|/h[1-6]|/tr)\\b[^>]*>")
    private val ANY_TAG = Regex("<[^>]*>")
    private val NUMERIC_ENTITY = Regex("&#(x?)([0-9a-fA-F]+);")
    private val ANY_ENTITY = Regex("&(#x?[0-9a-fA-F]+|[a-zA-Z]+);")
    private val NAMED_ENTITIES = mapOf(
        "&amp;" to "&", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&apos;" to "'", "&#39;" to "'",
        "&nbsp;" to " ", "&mdash;" to "—", "&ndash;" to "–", "&hellip;" to "…",
    )

    private val RTF_PARAGRAPH = Regex("\\\\(par|line)\\b ?")
    private val RTF_UNICODE = Regex("\\\\u(-?\\d+)\\??")
    private val RTF_HEX = Regex("\\\\'([0-9a-fA-F]{2})")
    private val RTF_CONTROL = Regex("\\\\[a-zA-Z]+-?\\d* ?")
    private val SPACES = Regex("[ \\t]+")
    private val BLANK_LINES = Regex("\\n\\s*\\n+")

    private const val UTF16_RANGE = 65536
    private const val HEX_RADIX = 16

    /**
     * What a module stores inside a verse besides its words, dropped whole: MySword's notes
     * (`<RF>…<Rf>`) and headings (`<TS>…<Ts>`), MyBible's Strong's numbers (`<S>…</S>`), morphology
     * (`<m>…</m>`), footnotes (`<f>…</f>`, `<n>…</n>`) and headings (`<h>…</h>`).
     */
    private val VERSE_ASIDES = Regex(
        "(?s)<RF[^>]*>.*?<Rf>|<TS>.*?<Ts>|<S>.*?</S>|<m>.*?</m>|<f>.*?</f>|<n>.*?</n>|<h>.*?</h>",
    )

    /** MySword's Strong's and morphology tags after a word (`<WG25>`, `<WTV-PAI>`), which carry no text. */
    private val WORD_TAGS = Regex("<W[GHT][^>]*>")

    /** An RTF control word -- how an e-Sword verse, which has no braces around it, is recognised. */
    private val RTF_WORD = Regex("\\\\[a-z]+-?\\d*")

    /**
     * One verse's text as a module stores it, as the words alone: notes, Strong's numbers and
     * headings dropped, MySword's `<CM>` paragraph mark and the rest of the markup removed.
     */
    fun fromVerse(raw: String): String {
        if (RTF_WORD.containsMatchIn(raw)) return fromRtf(raw).replace('\n', ' ').trim()
        val bare = raw.replace(VERSE_ASIDES, "").replace(WORD_TAGS, "").replace("<CM>", " ")
        return clean(bare).replace('\n', ' ').replace(Regex(" +"), " ").trim()
    }

    /** [raw] as plain text, whichever of HTML or RTF it is written in. */
    fun clean(raw: String): String = when {
        raw.trimStart().startsWith("{\\") || raw.contains("\\par") -> fromRtf(raw)
        (raw.contains('<') && raw.contains('>')) || ANY_ENTITY.containsMatchIn(raw) -> fromHtml(raw)
        else -> tidy(raw)
    }

    fun fromHtml(html: String): String {
        val text = html.replace(BREAK_TAG, "\n").replace(ANY_TAG, "")
        return tidy(decodeEntities(text))
    }

    fun fromRtf(rtf: String): String {
        val text = rtf
            .replace(RTF_PARAGRAPH, "\n")
            .replace(RTF_UNICODE) { m ->
                val code = m.groupValues[1].toInt().let { if (it < 0) it + UTF16_RANGE else it }
                code.toChar().toString()
            }
            .replace(RTF_HEX) { m -> m.groupValues[1].toInt(HEX_RADIX).toChar().toString() }
            .replace(RTF_CONTROL, "")
            .replace("{", "")
            .replace("}", "")
            .replace("\\\\", "\\")
        return tidy(text)
    }

    private fun decodeEntities(text: String): String {
        var out = text.replace(NUMERIC_ENTITY) { m ->
            val radix = if (m.groupValues[1].isEmpty()) 10 else HEX_RADIX
            // A code past Unicode's range is left as written rather than failing the whole import.
            m.groupValues[2].toIntOrNull(radix)?.takeIf(Character::isValidCodePoint)
                ?.let { String(Character.toChars(it)) } ?: m.value
        }
        NAMED_ENTITIES.forEach { (entity, value) -> out = out.replace(entity, value) }
        return out
    }

    /** Runs of spaces folded, blank lines collapsed to one, each line trimmed. */
    private fun tidy(text: String): String =
        text.replace("\r\n", "\n").replace('\r', '\n')
            .lines().joinToString("\n") { it.replace(SPACES, " ").trim() }
            .replace(BLANK_LINES, "\n\n")
            .trim()
}
