package com.callcontrol.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callcontrol.app.MainViewModel
import com.callcontrol.app.displayNumber
import com.callcontrol.app.label
import com.callcontrol.app.ltr
import com.callcontrol.domain.ContactStatus
import com.callcontrol.storage.CallLogEntry
import com.callcontrol.storage.RepoResult
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val logTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

fun formatLogTime(millis: Long): String =
    logTimeFormat.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

@Composable
fun LogScreen(vm: MainViewModel) {
    val state by vm.log.collectAsStateWithLifecycle()
    val entries = (state as? RepoResult.Success)?.value.orEmpty()
    var confirm by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = "سجل المكالمات",
        onBack = { vm.back() },
        actions = {
            if (entries.isNotEmpty()) {
                IconButton(onClick = { confirm = true }, modifier = Modifier.testTag("btn_clear_log")) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = "مسح السجل")
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Notice("سجل محلي على هاتفك فقط، لا يُرسل لأي جهة. يحفظ آخر 300 مكالمة عالجها التطبيق، ولا يحفظ رقم المتصل المخفي لأنه غير معروف. يمكنك إيقافه أو مسحه من الإعدادات.")
            if (entries.isEmpty()) {
                Text(
                    "لا توجد مكالمات مسجلة بعد.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp).testTag("empty_log"),
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 12.dp).testTag("log_list"),
                ) {
                    items(entries, key = { it.id }) { LogRow(it) }
                }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("مسح السجل بالكامل؟") },
            confirmButton = {
                TextButton(onClick = { confirm = false; vm.clearLog() }, modifier = Modifier.testTag("btn_confirm_clear_log")) { Text("مسح") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("إلغاء") } },
        )
    }
}

@Composable
private fun LogRow(e: CallLogEntry) {
    AppCard(modifier = Modifier.testTag("log_row")) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (e.hidden || e.numberKey == null) "رقم مخفي" else ltr(displayNumber(e.numberKey)),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                ModeChip(e.mode)
            }
            Text(formatLogTime(e.timestampMillis), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("السبب: ${e.reason.label()}", style = MaterialTheme.typography.bodyMedium)
            if (!e.hidden) {
                Text(
                    when (e.contactStatus) {
                        ContactStatus.IN_CONTACTS -> "محفوظ في جهات الاتصال"
                        ContactStatus.NOT_IN_CONTACTS -> "غير محفوظ في جهات الاتصال"
                        ContactStatus.UNKNOWN -> "تعذّر التحقق من جهات الاتصال"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (e.errorType != null) {
                Text("حدث خطأ (${e.errorType}) وسُمحت المكالمة أو لم يُطبَّق الإجراء.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
