package org.churchpresenter.bibleformats

import kotlin.test.Test
import kotlin.test.assertEquals

/** [ModuleMarkup] — HTML and RTF definitions as the plain text the tab and the output show. */
class ModuleMarkupTest {

    @Test
    fun `HTML loses its tags, keeps its line breaks and decodes its entities`() {
        val html = "<p><b>Aaron</b> &mdash; the elder brother of Moses.</p><p>See&nbsp;Exodus&#32;4:14 &amp; 6:20</p>"

        assertEquals("Aaron — the elder brother of Moses.\nSee Exodus 4:14 & 6:20", ModuleMarkup.clean(html))
    }

    @Test
    fun `a hexadecimal entity and an unknown one are handled`() {
        assertEquals("Ἀβαδδών &bogus;", ModuleMarkup.fromHtml("&#x1F08;βαδδών &bogus;"))
    }

    @Test
    fun `RTF loses its control words and keeps paragraphs and unicode`() {
        val rtf = """{\rtf1 {\b Abba}\par Father, in Aramaic: \u945?\u946? \'e9t\'e9\par}"""

        assertEquals("Abba\nFather, in Aramaic: αβ été", ModuleMarkup.clean(rtf))
    }

    @Test
    fun `a negative RTF unicode code is read as the character above 32767`() {
        assertEquals("ﬁ", ModuleMarkup.fromRtf("""\u-1279?"""))
    }

    @Test
    fun `plain text is only tidied`() {
        assertEquals("one line\n\nnext paragraph", ModuleMarkup.clean("  one   line \r\n\r\n\r\n next paragraph  "))
    }
}
