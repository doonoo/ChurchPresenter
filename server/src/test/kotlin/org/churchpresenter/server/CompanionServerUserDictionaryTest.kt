package org.churchpresenter.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.dictionary.data.UserDictionaryLibrary
import org.churchpresenter.settings.utils.Constants
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The imported dictionaries a phone reads: `GET /api/dictionaries`, `/api/dictionaries/{id}` and
 * `/api/dictionary/{number}/lexicons`, over a library in a folder of this suite's own -- and an
 * imported entry sent back as a schedule item.
 */
class CompanionServerUserDictionaryTest {

    private lateinit var root: File
    private lateinit var library: UserDictionaryLibrary
    private lateinit var server: CompanionServer
    private lateinit var client: HttpClient
    private var port: Int = 0
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("cp-server-user-dictionaries").toFile()
        library = UserDictionaryLibrary(File(root, "dictionaries"))
        server = CompanionServer(shutdownGraceMs = 0, dictionaryLibrary = { library })
        server.start(port = testPort(39_735))
        port = runBlocking {
            withTimeoutOrNull(10_000) {
                while (!server.isRunning.value || server.serverUrl.value.isBlank()) {
                    kotlinx.coroutines.delay(25)
                }
                server.serverUrl.value.substringAfterLast(':').toInt()
            }
        } ?: error("server did not start")
        client = HttpClient(CIO)
    }

    @AfterTest
    fun tearDown() {
        runCatching { client.close() }
        runCatching { server.stop() }
        root.deleteRecursively()
    }

    private fun url(path: String) = "http://127.0.0.1:$port$path"

    private fun importEastons() = library.import(
        File(root, "eastons.json").apply {
            writeText(
                """{"name":"Easton's Bible Dictionary","language":"en","entries":[
                {"word":"Aaron","definition":"The eldest son of Amram."},
                {"word":"Abba","definition":"Father, in Aramaic."},
                {"word":"Abel","definition":"Breath; the second son of Adam."}]}"""
            )
        },
    )

    private fun importThayer() = library.import(
        File(root, "thayer.tsv").apply { writeText("strongs\tlemma\tdefinition\nG26\tἀγάπη\taffection, good-will\n") },
        name = "Thayer's",
    )

    private suspend fun entries(path: String) =
        json.decodeFromString(ListSerializer(UserDictionaryEntryDto.serializer()), client.get(url(path)).bodyAsText())

    @Test
    fun `with nothing imported the list is empty`() = runBlocking {
        val response = client.get(url(Constants.ENDPOINT_USER_DICTIONARIES))

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("[]", response.bodyAsText())
    }

    @Test
    fun `the imported dictionaries are listed, each marked lexicon or not`() = runBlocking {
        val eastons = importEastons()
        val thayer = importThayer()

        val listed = json.decodeFromString(
            ListSerializer(UserDictionaryDto.serializer()),
            client.get(url(Constants.ENDPOINT_USER_DICTIONARIES)).bodyAsText(),
        )

        assertEquals(
            listOf(
                UserDictionaryDto(eastons.id, "Easton's Bible Dictionary", "en", 3, isLexicon = false),
                UserDictionaryDto(thayer.id, "Thayer's", "", 1, isLexicon = true),
            ),
            listed,
        )
    }

    @Test
    fun `a dictionary is searched by headword or definition, any case, up to the limit`() = runBlocking {
        val id = importEastons().id

        assertEquals(3, entries("/api/dictionaries/$id").size, "no query lists every entry")
        assertEquals(listOf("Aaron"), entries("/api/dictionaries/$id?q=aaron").map { it.word })
        assertEquals(listOf("Abba"), entries("/api/dictionaries/$id?q=ARAMAIC").map { it.word })
        assertEquals(1, entries("/api/dictionaries/$id?limit=1").size)
    }

    @Test
    fun `an entry gives its key to send back and the reference the output shows`() = runBlocking {
        val id = importEastons().id

        val aaron = entries("/api/dictionaries/$id?q=Aaron").single()
        assertEquals("$id:Aaron", aaron.key)
        assertEquals("Easton's Bible Dictionary", aaron.source)
        assertEquals("Easton's Bible Dictionary", aaron.reference)
    }

    @Test
    fun `an unknown dictionary is not found`() = runBlocking {
        val response = client.get(url("/api/dictionaries/dict-missing"))

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `a Strong's number's lexicon entries are listed, and none for a number no lexicon has`() = runBlocking {
        importEastons()
        importThayer()

        val g26 = entries("/api/dictionary/g26/lexicons")
        assertEquals(listOf("affection, good-will"), g26.map { it.definition })
        assertEquals("G26 · Thayer's", g26.single().reference)
        assertTrue(entries("/api/dictionary/H430/lexicons").isEmpty())
    }

    @Test
    fun `a dictionary imported while the server runs is found`() = runBlocking {
        assertEquals("[]", client.get(url(Constants.ENDPOINT_USER_DICTIONARIES)).bodyAsText())
        val id = importEastons().id

        assertEquals(3, entries("/api/dictionaries/$id").size)
    }

    @Test
    fun `an imported entry sent back as a schedule item keeps its dictionary`() {
        val item = RemoteItemDto(
            id = "r1", strongsNumber = "dict-eastons:Aaron", title = "Aaron",
            definition = "The eldest son of Amram.", dictionarySource = "Easton's Bible Dictionary",
        ).toScheduleItem()

        val dictionary = assertIs<ScheduleItem.DictionaryItem>(item)
        assertEquals("Easton's Bible Dictionary", dictionary.source)
        assertEquals("Aaron (Easton's Bible Dictionary)", dictionary.displayText)
        assertEquals("Aaron: The eldest son of Amram.", dictionary.toDto().text, "no empty brackets")
    }
}
