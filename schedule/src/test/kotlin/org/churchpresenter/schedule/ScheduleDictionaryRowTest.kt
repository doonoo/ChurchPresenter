package org.churchpresenter.schedule

import org.churchpresenter.core.models.dictionary.StrongsEntry
import org.churchpresenter.core.models.schedule.ScheduleItem
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** A dictionary entry added to the schedule keeps all the output card shows of it, its dictionary too. */
class ScheduleDictionaryRowTest {

    private lateinit var tempHome: File
    private var realHome: String? = null
    private val created = mutableListOf<ScheduleViewModel>()

    @BeforeTest
    fun isolateHome() {
        realHome = System.getProperty("user.home")
        tempHome = Files.createTempDirectory("cp-schedule-dictionary-test").toFile()
        System.setProperty("user.home", tempHome.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
        realHome?.let { System.setProperty("user.home", it) }
        tempHome.deleteRecursively()
    }

    @Test
    fun `an imported entry is added with its dictionary, labelled by it`() {
        val vm = ScheduleViewModel().also { created += it }
        val entry = StrongsEntry(
            "dict-eastons:Aaron", "Aaron", "", "", "The eldest son of Amram.", source = "Easton's Bible Dictionary",
        )

        vm.addDictionary(entry)

        val row = vm.scheduleItems.single() as ScheduleItem.DictionaryItem
        assertEquals(entry, row.entry)
        assertEquals("Aaron (Easton's Bible Dictionary)", row.displayText)
    }

    @Test
    fun `a Strong's entry keeps its pronunciation and usage`() {
        val vm = ScheduleViewModel().also { created += it }

        vm.addDictionary(StrongsEntry("H430", "Elohim", "el-o-heem", "el-o-heem'", "God", kjvUsage = "God (2346x)"))

        val row = vm.scheduleItems.single() as ScheduleItem.DictionaryItem
        assertEquals("el-o-heem'", row.pronunciation)
        assertEquals("God (2346x)", row.kjvUsage)
        assertEquals("Elohim (H430)", row.displayText)
    }
}
