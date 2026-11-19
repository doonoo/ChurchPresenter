package org.churchpresenter.dictionary.data

import kotlin.test.Test
import kotlin.test.assertEquals

/** [DelimitedText] — CSV and TSV split the way a spreadsheet writes them. */
class DelimitedTextTest {

    @Test
    fun `quoted fields hold separators, doubled quotes and line breaks`() {
        val rows = DelimitedText.parse("a,\"b, \"\"c\"\"\nd\"\r\ne,f", ',')

        assertEquals(listOf(listOf("a", "b, \"c\"\nd"), listOf("e", "f")), rows)
    }

    @Test
    fun `blank lines are skipped and a missing last newline still ends the row`() {
        assertEquals(listOf(listOf("x", "y"), listOf("z", "")), DelimitedText.parse("x\ty\n\n\nz\t", '\t'))
    }

    @Test
    fun `a quote inside an unquoted field is kept as text`() {
        assertEquals(listOf(listOf("5\" scroll", "b")), DelimitedText.parse("5\" scroll,b", ','))
    }
}
