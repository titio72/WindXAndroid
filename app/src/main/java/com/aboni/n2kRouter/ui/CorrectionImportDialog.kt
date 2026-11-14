package com.aboni.n2kRouter.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import com.aboni.marine.ui.theme.Marine
import com.aboni.n2kRouter.CorrectionRow
import com.aboni.n2kRouter.parseImport
import com.aboni.n2kRouter.rowSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** A table file is a few hundred characters; anything far beyond that is not one. */
private const val MAX_IMPORT_CHARS = 20_000

/**
 * Asks for the text the router script prints (`U` lines), pasted or read from a file, checks it with [parseImport] as
 * it changes and shows what would be sent or what is wrong. Nothing is sent until the user confirms.
 */
@Composable
fun CorrectionImportDialog(onSend: (List<CorrectionRow>) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    var fileError by remember { mutableStateOf<String?>(null) }
    val result = remember(text) { parseImport(text) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText().take(MAX_IMPORT_CHARS) }
                        ?: throw IOException("no stream")
                }
                fileError = null
            } catch (e: IOException) {
                fileError = "Can't read that file"
            } catch (e: SecurityException) {
                fileError = "Can't read that file"
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import table") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(MAX_IMPORT_CHARS) },
                    label = { Text("Paste the U lines") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { picker.launch(arrayOf("text/*", "application/octet-stream")) }) { Text("Open file…") }
                fileError?.let { Text(it, style = Marine.type.body, color = Marine.colors.error) }
                result.errors.forEach {
                    val where = if (it.line > 0) "Line ${it.line}" else "Table"
                    Text("$where: ${it.reason}", style = Marine.type.body, color = Marine.colors.error)
                }
                if (result.errors.isEmpty() && result.rows.isNotEmpty()) {
                    Text("${result.rows.size} rows will replace the sensor's table", style = Marine.type.body, color = Marine.colors.onSurface)
                    result.rows.forEach {
                        Text(rowSummary(it), style = Marine.type.caption.copy(fontFamily = FontFamily.Monospace), color = Marine.colors.onSurface)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSend(result.rows); onDismiss() },
                enabled = result.errors.isEmpty() && result.rows.isNotEmpty(),
            ) { Text("Send") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
