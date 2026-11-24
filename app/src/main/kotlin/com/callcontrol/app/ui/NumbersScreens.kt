package com.callcontrol.app.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callcontrol.app.MainViewModel
import com.callcontrol.app.Screen
import com.callcontrol.app.displayNumber
import com.callcontrol.app.ltr
import com.callcontrol.app.toMessage
import com.callcontrol.domain.Mode
import com.callcontrol.storage.RepoResult
import com.callcontrol.storage.StoredNumberRule

/** Digits only, Arabic-Indic digits converted, so "٠١٠" matches "010" when searching. */
private fun searchDigits(text: String): String = buildString {
    for (c in text) {
        when {
            c in '0'..'9' -> append(c)
            c in '٠'..'٩' -> append('0' + (c - '٠'))
            c in '۰'..'۹' -> append('0' + (c - '۰'))
        }
    }
}

/** Search by name (any part) or number (any part, any spelling: 010..., +2010..., 002010...). */
internal fun matchesQuery(rule: StoredNumberRule, query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    if (rule.name?.contains(q, ignoreCase = true) == true) return true
    if (rule.notes?.contains(q, ignoreCase = true) == true) return true
    val digits = searchDigits(q)
    if (digits.isEmpty()) return false
    val local = displayNumber(rule.numberKey).filter { it.isDigit() }
    val international = rule.numberKey.filter { it.isDigit() }
    val trimmedDigits = digits.removePrefix("00")
    return local.contains(digits) || international.contains(trimmedDigits)
}

@Composable
fun NumbersScreen(vm: MainViewModel) {
    val state by vm.rules.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val all = (state as? RepoResult.Success)?.value.orEmpty().reversed() // newest first
    val shown = all.filter { matchesQuery(it, query) }

    ScreenScaffold(
        title = "الاستثناءات",
        onBack = { vm.back() },
        floatingActionButton = {
            FloatingActionButton(onClick = { vm.navigate(Screen.EditNumber(null)) }, modifier = Modifier.testTag("fab_add")) {
                Icon(Icons.Filled.Add, contentDescription = "إضافة رقم")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().testTag("field_search"),
                placeholder = { Text("ابحث بالاسم أو الرقم") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            if (all.isEmpty()) {
                Text(
                    "لا توجد استثناءات بعد. اضغط + لإضافة رقم مسموح.",
                    modifier = Modifier.padding(16.dp).testTag("empty_rules"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (shown.isEmpty()) {
                Text("لا نتائج مطابقة.", modifier = Modifier.padding(16.dp).testTag("no_results"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                items(shown, key = { it.id }) { rule ->
                    AppCard(onClick = { vm.navigate(Screen.EditNumber(rule.id)) }, modifier = Modifier.testTag("rule_row")) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(14.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(rule.name ?: ltr(displayNumber(rule.numberKey)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                if (rule.name != null) {
                                    Text(ltr(displayNumber(rule.numberKey)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(Modifier.height(6.dp))
                                ModeChip(rule.mode)
                            }
                            Switch(
                                checked = rule.enabled,
                                onCheckedChange = { vm.setRuleEnabled(rule.id, it) },
                                modifier = Modifier.testTag("rule_switch"),
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(88.dp)) }
            }
        }
    }
}

/** Reads name + number of the contact the user picked. Returns null if the row cannot be read. */
private fun readPickedContact(context: Context, uri: Uri): Pair<String?, String>? {
    val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
    val cursor = context.contentResolver.query(uri, projection, null, null, null) ?: return null
    return cursor.use {
        if (!it.moveToFirst()) null
        else {
            val number = it.getString(0) ?: return@use null
            val name = it.getString(1)
            name to number
        }
    }
}

/** Add (id == null) or edit (id != null) one per-number rule. Deleting a rule never touches the real contact. */
@Composable
fun EditNumberScreen(vm: MainViewModel, id: Long?) {
    val context = LocalContext.current
    val rulesState by vm.rules.collectAsStateWithLifecycle()
    val existing: StoredNumberRule? = id?.let { wanted -> (rulesState as? RepoResult.Success)?.value?.firstOrNull { it.id == wanted } }

    var loaded by rememberSaveable(id) { mutableStateOf(false) }
    var number by rememberSaveable(id) { mutableStateOf("") }
    var name by rememberSaveable(id) { mutableStateOf("") }
    var notes by rememberSaveable(id) { mutableStateOf("") }
    var mode by rememberSaveable(id) { mutableStateOf(Mode.AVAILABLE) }
    var enabled by rememberSaveable(id) { mutableStateOf(true) }
    var error by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    // Fill the form once from the stored rule (editing).
    if (existing != null && !loaded) {
        number = existing.numberKey
        name = existing.name.orEmpty()
        notes = existing.notes.orEmpty()
        mode = existing.mode
        enabled = existing.enabled
        loaded = true
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            val picked = try {
                readPickedContact(context, uri)
            } catch (e: SecurityException) {
                null
            }
            if (picked != null) {
                number = picked.second
                if (name.isBlank()) name = picked.first.orEmpty()
                error = null
            } else {
                error = "تعذّرت قراءة رقم جهة الاتصال المختارة."
            }
        }
    }

    ScreenScaffold(
        title = if (id == null) "إضافة استثناء" else "تعديل الاستثناء",
        onBack = { vm.back() },
        actions = {
            if (id != null) {
                IconButton(onClick = { confirmDelete = true }, modifier = Modifier.testTag("btn_delete")) {
                    Icon(Icons.Filled.Delete, contentDescription = "حذف القاعدة")
                }
            }
        },
    ) { padding ->
        if (id != null && existing == null && !loaded) {
            Text("القاعدة غير موجودة (ربما حُذفت).", modifier = Modifier.padding(padding).padding(16.dp).testTag("rule_missing"))
            return@ScreenScaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = number,
                onValueChange = { number = it; error = null },
                label = { Text("رقم الهاتف") },
                placeholder = { Text("01012345678 أو +201012345678") },
                singleLine = true,
                isError = error != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                textStyle = TextStyle(textDirection = TextDirection.Ltr),
                modifier = Modifier.fillMaxWidth().testTag("field_number"),
            )
            if (error != null) {
                Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("error_text"))
            }
            OutlinedButton(
                onClick = {
                    try {
                        picker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
                    } catch (e: ActivityNotFoundException) {
                        error = "لا يوجد تطبيق جهات اتصال على هذا الجهاز."
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("btn_pick_contact"),
            ) {
                Icon(Icons.Filled.Contacts, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("اختيار من جهات الاتصال")
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("الاسم (اختياري)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("field_name"),
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("ملاحظات (اختياري)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth().testTag("field_notes"),
            )
            SectionTitle("ماذا يحدث عندما يتصل هذا الرقم؟")
            ModeSelector(selected = mode, onSelect = { mode = it }, tagPrefix = "rule_mode")
            if (id != null) {
                AppCard { SwitchRow("القاعدة مفعّلة", "إيقافها يُبقيها محفوظة دون تطبيق.", enabled, { enabled = it }, "switch_rule_enabled") }
            }
            Button(
                enabled = !saving,
                onClick = {
                    saving = true
                    val finish: (RepoResult<StoredNumberRule>) -> Unit = { result ->
                        saving = false
                        when (result) {
                            is RepoResult.Success -> vm.back()
                            is RepoResult.Failure -> error = result.error.toMessage()
                        }
                    }
                    if (id == null) vm.addRule(number, mode, name, notes, true, finish)
                    else vm.updateRule(id, number, mode, enabled, name, notes, finish)
                },
                modifier = Modifier.fillMaxWidth().testTag("btn_save"),
            ) { Text("حفظ") }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete && id != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("حذف القاعدة؟") },
            text = { Text("ستُحذف هذه القاعدة فقط. لن تُحذف جهة الاتصال من هاتفك.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        vm.deleteRule(id) { result ->
                            when (result) {
                                is RepoResult.Success -> vm.back()
                                is RepoResult.Failure -> error = result.error.toMessage()
                            }
                        }
                    },
                    modifier = Modifier.testTag("btn_confirm_delete"),
                ) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("إلغاء") } },
        )
    }
}
