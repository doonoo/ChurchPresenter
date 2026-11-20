package org.churchpresenter.server

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.churchpresenter.settings.utils.Constants

private const val DEFAULT_SEARCH_LIMIT = 100

/**
 * The dictionaries and lexicons the operator imported, for phones: `GET /api/dictionaries`, a search
 * through one with `GET /api/dictionaries/{id}`, and `GET /api/dictionary/{number}/lexicons` -- what
 * each lexicon says of a Strong's number. A phone shows or schedules an entry by sending its `key` as
 * the schedule item's `strongsNumber`, with its `source` as `dictionarySource`.
 */
internal fun Route.userDictionaryRoutes(server: CompanionServer, json: Json) {
    val dictionaries = server.userDictionaries

    get(Constants.ENDPOINT_USER_DICTIONARIES) {
        if (!server.checkApiKey(call)) return@get
        call.respondText(
            json.encodeToString(ListSerializer(UserDictionaryDto.serializer()), dictionaries.list()),
            ContentType.Application.Json,
        )
    }

    get(Constants.ENDPOINT_USER_DICTIONARY) {
        if (!server.checkApiKey(call)) return@get
        val id = call.parameters["id"].orEmpty()
        val query = call.request.queryParameters["q"].orEmpty()
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: DEFAULT_SEARCH_LIMIT
        val entries = dictionaries.search(id, query, limit)
        if (entries == null) {
            call.respond(HttpStatusCode.NotFound, """{"error":"dictionary not found"}""")
            return@get
        }
        call.respondText(
            json.encodeToString(ListSerializer(UserDictionaryEntryDto.serializer()), entries),
            ContentType.Application.Json,
        )
    }

    get(Constants.ENDPOINT_DICTIONARY_LEXICONS) {
        if (!server.checkApiKey(call)) return@get
        val number = call.parameters["number"].orEmpty()
        call.respondText(
            json.encodeToString(ListSerializer(UserDictionaryEntryDto.serializer()), dictionaries.lexicons(number)),
            ContentType.Application.Json,
        )
    }
}
