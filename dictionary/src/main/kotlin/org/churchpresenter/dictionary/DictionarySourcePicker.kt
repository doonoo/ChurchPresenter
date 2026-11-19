package org.churchpresenter.dictionary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.dictionary.data.DictionaryImport
import org.churchpresenter.dictionary.data.UserDictionaryInfo
import org.churchpresenter.sharedui.composables.ActionIconButton
import org.churchpresenter.sharedui.filechooser.FileChooser
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.cancel
import org.churchpresenter.strings.generated.resources.dictionary_import
import org.churchpresenter.strings.generated.resources.dictionary_import_failed
import org.churchpresenter.strings.generated.resources.dictionary_import_filter
import org.churchpresenter.strings.generated.resources.dictionary_import_title
import org.churchpresenter.strings.generated.resources.dictionary_remove
import org.churchpresenter.strings.generated.resources.dictionary_remove_text
import org.churchpresenter.strings.generated.resources.dictionary_remove_title
import org.churchpresenter.strings.generated.resources.dictionary_source_label
import org.churchpresenter.strings.generated.resources.dictionary_source_strongs
import org.churchpresenter.strings.generated.resources.remove
import org.churchpresenter.theme.components.DropdownSelector
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.io.File
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.icons.generated.resources.ic_close
import org.churchpresenter.icons.generated.resources.ic_delete

/** The picker's key for Strong's, which has no id in the library. */
private const val STRONGS_KEY = ""

/** The picker's last row, which opens the file chooser rather than choosing a dictionary. */
private const val IMPORT_KEY = "\u0000import"

/** The platform's file chooser, asking for one dictionary file; null when the operator cancels. */
@Composable
internal fun rememberDictionaryFileChooser(): suspend () -> File? {
    val title = stringResource(Res.string.dictionary_import_title)
    val filterLabel = stringResource(Res.string.dictionary_import_filter)
    return remember(title, filterLabel) {
        {
            FileChooser.platformInstance.chooseSingle(
                path = null,
                filters = listOf(DictionaryImport.fileFilter(filterLabel)),
                title = title,
                selectDirectory = false,
            )?.toFile()
        }
    }
}

/**
 * Which dictionary the tab reads: Strong's, every imported one, and a last row that imports another.
 * Beside it, Remove for an imported dictionary, behind a confirmation; under it, why an import failed.
 */
@Composable
internal fun DictionarySourceRow(viewModel: DictionaryViewModel, onImport: () -> Unit) {
    val options = listOf(STRONGS_KEY to stringResource(Res.string.dictionary_source_strongs)) +
        viewModel.userDictionaries.map { it.id to it.name } +
        (IMPORT_KEY to stringResource(Res.string.dictionary_import))
    var confirmRemove by remember { mutableStateOf<UserDictionaryInfo?>(null) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DropdownSelector(
            label = stringResource(Res.string.dictionary_source_label),
            value = viewModel.activeSourceId ?: STRONGS_KEY,
            options = options,
            onValueChange = { key ->
                when (key) {
                    IMPORT_KEY -> onImport()
                    STRONGS_KEY -> viewModel.selectSource(null)
                    else -> viewModel.selectSource(key)
                }
            },
            modifier = Modifier.weight(1f),
            enabled = !viewModel.isImporting,
        )
        if (viewModel.isImporting) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        }
        viewModel.activeDictionary?.let { dictionary ->
            ActionIconButton(
                onClick = { confirmRemove = dictionary },
                tooltipText = stringResource(Res.string.dictionary_remove),
                painter = painterResource(IconRes.drawable.ic_delete),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    viewModel.importError?.let { error -> ImportErrorLine(error, onDismiss = viewModel::dismissImportError) }

    confirmRemove?.let { dictionary ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text(stringResource(Res.string.dictionary_remove_title, dictionary.name)) },
            text = { Text(stringResource(Res.string.dictionary_remove_text)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeDictionary(dictionary.id)
                    confirmRemove = null
                }) { Text(stringResource(Res.string.remove)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = null }) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }
}

@Composable
private fun ImportErrorLine(error: DictionaryImportError, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(Res.string.dictionary_import_failed, error.fileName, error.reason),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        ActionIconButton(
            onClick = onDismiss,
            tooltipText = stringResource(Res.string.cancel),
            painter = painterResource(IconRes.drawable.ic_close),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            buttonSize = 26.dp,
            iconSize = 12.dp,
        )
    }
}
