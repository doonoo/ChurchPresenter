package org.churchpresenter.dictionary.data

/**
 * Splits CSV or TSV into rows of fields, honouring quotes the way spreadsheets write them: a quoted
 * field may hold the separator, line breaks and `""` for a quote. Splitting on the separator alone
 * would cut every definition that contains a comma.
 */
internal object DelimitedText {

    fun parse(text: String, separator: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                c == '"' && (quoted || field.isEmpty()) -> quoted = !quoted
                quoted -> field.append(c)
                c == separator -> { row += field.toString(); field.setLength(0) }
                c == '\n' || c == '\r' -> {
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                    row += field.toString(); field.setLength(0)
                    if (row.any { it.isNotBlank() }) rows += row.toList()
                    row.clear()
                }
                else -> field.append(c)
            }
            i++
        }
        row += field.toString()
        if (row.any { it.isNotBlank() }) rows += row.toList()
        return rows
    }
}
