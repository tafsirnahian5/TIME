package com.example.timeapp.ui.money

import com.example.timeapp.data.L

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.example.timeapp.data.AttachmentStore
import com.example.timeapp.data.db.AttachmentEntity
import com.example.timeapp.data.db.TimeDatabase
import com.example.timeapp.ui.theme.Dimens
import kotlinx.coroutines.launch

/**
 * A transaction's receipts (Phase 13): add a photo or PDF (copied in, checked for type and
 * size), open one in a viewer, or remove it. Included in backups.
 */
@Composable
fun ReceiptsDialog(recordId: Long, title: String, receipts: List<AttachmentEntity>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember { AttachmentStore(context.applicationContext, TimeDatabase.getInstance(context.applicationContext)) }
    val scope = rememberCoroutineScope()
    var problem by remember { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf<AttachmentEntity?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { problem = store.attach(recordId, uri) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L("Receipts · %1\$s", title)) },
        text = {
            Column {
                if (receipts.isEmpty()) Text(L("None yet. Photos and PDFs up to 10 MB; a copy is kept in T!ME and in your backups."), style = MaterialTheme.typography.bodySmall)
                receipts.forEach { r ->
                    Row(Modifier.fillMaxWidth().heightIn(min = Dimens.touchMin), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            r.displayName ?: r.fileName,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f).clickable {
                                val uri = FileProvider.getUriForFile(context, context.packageName + ".files", store.file(r))
                                val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, r.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                runCatching { context.startActivity(Intent.createChooser(view, L("Open receipt"))) }
                                    .onFailure { problem = L("No app here can open that file.") }
                            }.padding(vertical = Dimens.s8),
                        )
                        TextButton(onClick = { removing = r }) { Text(L("Remove")) }
                    }
                }
                problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Dimens.s6)) }
            }
        },
        confirmButton = { TextButton(onClick = { problem = null; pick.launch(arrayOf("image/*", "application/pdf")) }) { Text(L("Add receipt")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("Close")) } },
    )
    removing?.let { r ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(L("Remove this receipt?")) },
            text = { Text(L("The copy kept in T!ME is deleted. The record stays.")) },
            confirmButton = { TextButton(onClick = { scope.launch { store.remove(r) }; removing = null }) { Text(L("Remove")) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(L("Keep")) } },
        )
    }
}
