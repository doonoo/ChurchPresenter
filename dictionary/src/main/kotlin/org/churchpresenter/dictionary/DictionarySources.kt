package org.churchpresenter.dictionary

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.core.models.dictionary.StrongsEntry
import org.churchpresenter.dictionary.data.UserDictionaryInfo
import org.churchpresenter.dictionary.data.UserDictionaryLibrary
import java.io.File

/** Which dictionary the tab reads — Strong's, or one the operator imported — and the importing itself. */
interface DictionarySourceActions {
    /** Re-reads the list of imported dictionaries, and the lexicons a Strong's entry is matched in. */
    fun refreshDictionaries(): Job

    /** Switches to the dictionary [id], or Strong's for null; `load()` then reads it. */
    fun selectSource(id: String?)

    /** Imports [file] into the library and switches to it; a failure is left in `importError`. */
    fun importDictionary(file: File): Job

    /** Deletes the dictionary [id]'s copy, going back to Strong's if it was the one being read. */
    fun removeDictionary(id: String): Job

    fun dismissImportError()
}

/** Why an import failed: the file's name and what was wrong with it. */
data class DictionaryImportError(val fileName: String, val reason: String)

internal class DictionarySources(
    private val state: DictionaryState,
    private val scope: CoroutineScope,
    private val selection: DictionarySelectionActions,
    library: () -> UserDictionaryLibrary,
    private val io: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
) : DictionarySourceActions {

    /** Resolving the app data folder touches the disk, so the library is made on first use, off the event thread. */
    private val library by lazy(library)

    override fun refreshDictionaries(): Job = scope.launch {
        val (infos, index) = withContext(io) {
            val listed = library.list()
            listed to lexiconIndex(listed)
        }
        state.userDictionaries = infos
        state.lexiconIndex = index
    }

    /** Clears what belonged to the old source; the tab's `load()`, keyed on the source, reads the new one. */
    override fun selectSource(id: String?): Unit = with(state) {
        if (activeSourceId == id) return
        activeSourceId = id
        clearPassageFilter()
        filterLanguage = DictionaryLanguageFilter.ALL
        searchQuery = ""
        selection.onEntrySelected(null, addToHistory = false)
        history.clear()
        historyIdx = -1
        entries = emptyList()
    }

    /** The entries of the source chosen, read off the event thread. */
    fun loadActive(): Job? {
        val id = state.activeSourceId ?: return null
        state.isLoading = true
        return scope.launch {
            try {
                val loaded = withContext(io) { library.load(id) }
                if (state.activeSourceId == id) state.entries = loaded
            } finally {
                state.isLoading = false
            }
        }
    }

    @Suppress("TooGenericExceptionCaught") // a file of any shape is handed in; every failure becomes a message
    override fun importDictionary(file: File): Job = scope.launch {
        state.isImporting = true
        state.importError = null
        try {
            val info = withContext(io) { library.import(file) }
            refreshDictionaries().join()
            selectSource(info.id)
        } catch (e: Exception) {
            state.importError = DictionaryImportError(file.name, e.message ?: e.javaClass.simpleName)
        } finally {
            state.isImporting = false
        }
    }

    override fun removeDictionary(id: String): Job = scope.launch {
        withContext(io) { library.remove(id) }
        if (state.activeSourceId == id) selectSource(null)
        refreshDictionaries().join()
    }

    override fun dismissImportError() {
        state.importError = null
    }

    /** Every lexicon's entries by Strong's number, so a Strong's entry can show what each says of it. */
    private fun lexiconIndex(infos: List<UserDictionaryInfo>): Map<String, List<StrongsEntry>> =
        infos.filter { it.isLexicon }
            .flatMap { library.load(it.id) }
            .filter { it.isStrongsNumber }
            .groupBy { it.number }
}
