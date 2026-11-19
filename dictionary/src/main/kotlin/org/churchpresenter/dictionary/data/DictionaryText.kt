package org.churchpresenter.dictionary.data

/**
 * Turns the marked-up definitions dictionary modules carry into the plain text the tab and the
 * output show: MySword and MyBible write HTML, e-Sword writes RTF, Zefania writes XML that the reader
 * has already reduced to text.
 */
internal object DictionaryText {

    private val BREAK_TAG = Regex("(?i)<\\s*(br|/p|/div|/li|/h[1-6]|/tr)\\b[^>]*>")
    private val ANY_TAG = Regex("<[^>]*>")
    private val NUMERIC_ENTITY = Regex("&#(x?)([0-9a-fA-F]+);")
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

    /** [raw] as plain text, whichever of HTML or RTF it is written in. */
    fun clean(raw: String): String = when {
        raw.trimStart().startsWith("{\\") || raw.contains("\\par") -> fromRtf(raw)
        raw.contains('<') && raw.contains('>') -> fromHtml(raw)
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
            m.groupValues[2].toIntOrNull(radix)?.let { String(Character.toChars(it)) } ?: m.value
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
