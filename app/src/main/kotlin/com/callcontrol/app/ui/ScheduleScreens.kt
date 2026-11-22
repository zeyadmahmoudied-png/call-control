@file:OptIn(ExperimentalLayoutApi::class)

package com.callcontrol.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callcontrol.app.MainViewModel
import com.callcontrol.app.Screen
import com.callcontrol.app.WEEK_ORDER
import com.callcontrol.app.daysSummary
import com.callcontrol.app.formatTime
import com.callcontrol.app.label
import com.callcontrol.app.toMessage
import com.callcontrol.domain.Mode
import com.callcontrol.domain.ScheduleEntry
import com.callcontrol.storage.AppSettings
import com.callcontrol.storage.RepoResult
import com.callcontrol.storage.ScheduleAnalysis
import com.callcontrol.storage.StoredScheduleEntry
import java.time.DayOfWeek
import java.time.LocalTime

@Composable
fun ScheduleScreen(vm: MainViewModel) {
    val settingsState by vm.settings.collectAsStateWithLifecycle()
    val entriesState by vm.entries.collectAsStateWithLifecycle()
    val settings = (settingsState as? RepoResult.Success)?.value ?: AppSettings()
    val entries = (entriesState as? RepoResult.Success)?.value.orEmpty()
    val overlaps = ScheduleAnalysis.findOverlaps(entries.map { it.entry })
    val overlapping = overlaps.flatMap { listOf(it.first, it.second) }.toSet()

    ScreenScaffold(
        title = "الجدولة",
        onBack = { vm.back() },
        floatingActionButton = {
            FloatingActionButton(onClick = { vm.navigate(Screen.EditEntry(null)) }, modifier = Modifier.testTag("fab_add_entry")) {
                Icon(Icons.Filled.Add, contentDescription = "إضافة فترة")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Notice(
                "تغيّر الجدولة «الوضع العام» تلقائيًا في الأوقات التي تحددها. تُحسب الحالة لحظة وصول المكالمة، فلا تتأثر بتوفير الطاقة ولا بإعادة تشغيل الهاتف. قواعد الأرقام والمخفي وغير المسجل أقوى من الجدولة ولا تتغير بها.",
            )
            AppCard {
                SwitchRow(
                    title = "تفعيل الجدولة",
                    subtitle = if (settings.scheduleEnabled) "مفعّلة" else "غير مفعّلة: يُستخدم الوضع العام الثابت.",
                    checked = settings.scheduleEnabled,
                    onCheckedChange = { on -> vm.updateSettings { it.copy(scheduleEnabled = on) } },
                    tag = "switch_schedule",
                )
            }

            SectionTitle("باقي اليوم (خارج الفترات)")
            DefaultModePicker(settings.scheduleDefaultMode) { picked -> vm.updateSettings { it.copy(scheduleDefaultMode = picked) } }

            SectionTitle("الفترات (الأولى في القائمة تتفوق عند التداخل)")
            if (overlaps.isNotEmpty()) {
                Notice(
                    "تنبيه: بعض الفترات تتداخل. عند التداخل تُطبَّق الفترة الأعلى في القائمة. استخدم الأسهم لتغيير الترتيب.",
                    error = true,
                    modifier = Modifier.testTag("notice_overlap"),
                )
            }
            if (entries.isEmpty()) {
                Text("لا توجد فترات. اضغط + لإضافة فترة.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("empty_entries"))
            }
            entries.forEachIndexed { index, stored ->
                EntryCard(
                    stored = stored,
                    overlapping = index in overlapping,
                    canMoveUp = index > 0,
                    canMoveDown = index < entries.lastIndex,
                    onOpen = { vm.navigate(Screen.EditEntry(stored.id)) },
                    onToggle = { vm.setEntryEnabled(stored, it) },
                    onUp = { vm.moveEntry(entries, index, -1) },
                    onDown = { vm.moveEntry(entries, index, +1) },
                )
            }
            Spacer(Modifier.height(88.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DefaultModePicker(selected: Mode?, onPick: (Mode?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FilterChip(
            selected = selected == null,
            onClick = { onPick(null) },
            label = { Text("الوضع العام الثابت") },
            modifier = Modifier.testTag("default_mode_none"),
        )
        Mode.values().forEach { m ->
            FilterChip(
                selected = selected == m,
                onClick = { onPick(m) },
                label = { Text(m.label()) },
                modifier = Modifier.testTag("default_mode_${m.name}"),
            )
        }
    }
}

@Composable
private fun EntryCard(
    stored: StoredScheduleEntry,
    overlapping: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    val e = stored.entry
    AppCard(onClick = onOpen, modifier = Modifier.testTag("entry_row")) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(daysSummary(e.days), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    val crosses = e.start > e.end
                    Text(
                        "من ${formatTime(e.start)} إلى ${formatTime(e.end)}" + if (crosses) " (اليوم التالي)" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = e.enabled, onCheckedChange = onToggle, modifier = Modifier.testTag("entry_switch"))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                ModeChip(e.mode)
                if (overlapping) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Filled.Warning, contentDescription = "تتداخل مع فترة أخرى", tint = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onUp, enabled = canMoveUp) { Icon(Icons.Filled.ArrowUpward, contentDescription = "رفع الأولوية") }
                IconButton(onClick = onDown, enabled = canMoveDown) { Icon(Icons.Filled.ArrowDownward, contentDescription = "خفض الأولوية") }
            }
        }
    }
}

/** Two numeric boxes (hour 0-23, minute 0-59). Calls [onValid] with the time, or null while the text is not a valid time. */
@Composable
private fun TimeInput(label: String, initial: LocalTime, tag: String, onValid: (LocalTime?) -> Unit) {
    var hour by rememberSaveable(tag) { mutableStateOf(initial.hour.toString().padStart(2, '0')) }
    var minute by rememberSaveable(tag) { mutableStateOf(initial.minute.toString().padStart(2, '0')) }

    fun publish(h: String, m: String) {
        val hv = h.toIntOrNull()
        val mv = m.toIntOrNull()
        onValid(if (hv != null && mv != null && hv in 0..23 && mv in 0..59) LocalTime.of(hv, mv) else null)
    }

    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = hour,
                onValueChange = { v -> hour = v.filter { it.isDigit() }.take(2); publish(hour, minute) },
                label = { Text("ساعة") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(96.dp).testTag("${tag}_hour"),
            )
            Text(":")
            OutlinedTextField(
                value = minute,
                onValueChange = { v -> minute = v.filter { it.isDigit() }.take(2); publish(hour, minute) },
                label = { Text("دقيقة") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(96.dp).testTag("${tag}_minute"),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEntryScreen(vm: MainViewModel, id: Long?) {
    val entriesState by vm.entries.collectAsStateWithLifecycle()
    val existing: StoredScheduleEntry? = id?.let { wanted -> (entriesState as? RepoResult.Success)?.value?.firstOrNull { it.id == wanted } }

    var loaded by rememberSaveable(id) { mutableStateOf(false) }
    var days by rememberSaveable(id) { mutableStateOf(DayOfWeek.values().map { it.name }.toSet()) }
    var mode by rememberSaveable(id) { mutableStateOf(Mode.BUSY) }
    var enabled by rememberSaveable(id) { mutableStateOf(true) }
    var start by remember(id) { mutableStateOf<LocalTime?>(LocalTime.of(8, 0)) }
    var end by remember(id) { mutableStateOf<LocalTime?>(LocalTime.of(14, 0)) }
    var error by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    val initial = existing?.entry
    if (initial != null && !loaded) {
        days = initial.days.map { it.name }.toSet()
        mode = initial.mode
        enabled = initial.enabled
        start = initial.start
        end = initial.end
        loaded = true
    }

    ScreenScaffold(
        title = if (id == null) "إضافة فترة" else "تعديل الفترة",
        onBack = { vm.back() },
        actions = {
            if (id != null) {
                IconButton(onClick = { confirmDelete = true }, modifier = Modifier.testTag("btn_delete_entry")) {
                    Icon(Icons.Filled.Delete, contentDescription = "حذف الفترة")
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("الأيام")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WEEK_ORDER.forEach { day ->
                    FilterChip(
                        selected = day.name in days,
                        onClick = { days = if (day.name in days) days - day.name else days + day.name; error = null },
                        label = { Text(day.label()) },
                        modifier = Modifier.testTag("day_${day.name}"),
                    )
                }
            }
            // The time boxes are keyed by the loaded state so they pick up the stored values once.
            key(loaded) {
                TimeInput("من", initial?.start ?: LocalTime.of(8, 0), "start") { start = it; error = null }
                TimeInput("إلى", initial?.end ?: LocalTime.of(14, 0), "end") { end = it; error = null }
            }
            Text(
                "إذا كانت النهاية قبل البداية (مثل 22:00 إلى 06:00) تمتد الفترة إلى اليوم التالي. النهاية 00:00 تعني حتى منتصف الليل.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SectionTitle("الحالة في هذه الفترة")
            ModeSelector(selected = mode, onSelect = { mode = it }, tagPrefix = "entry_mode")
            if (id != null) {
                AppCard { SwitchRow("الفترة مفعّلة", null, enabled, { enabled = it }, "switch_entry_enabled") }
            }
            if (error != null) {
                Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("error_text"))
            }
            Button(
                onClick = {
                    val s = start
                    val e = end
                    if (s == null || e == null) {
                        error = "اكتب وقتًا صحيحًا (الساعة من 0 إلى 23 والدقيقة من 0 إلى 59)."
                        return@Button
                    }
                    val entry = ScheduleEntry(
                        days = days.map { DayOfWeek.valueOf(it) }.toSet(),
                        start = s, end = e, mode = mode, enabled = enabled,
                    )
                    val finish: (RepoResult<StoredScheduleEntry>) -> Unit = { r ->
                        when (r) {
                            is RepoResult.Success -> vm.back()
                            is RepoResult.Failure -> error = r.error.toMessage()
                        }
                    }
                    if (id == null) vm.addEntry(entry, finish) else vm.updateEntry(id, entry, finish)
                },
                modifier = Modifier.fillMaxWidth().testTag("btn_save_entry"),
            ) { Text("حفظ") }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete && id != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("حذف الفترة؟") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteEntry(id) { r ->
                        when (r) {
                            is RepoResult.Success -> vm.back()
                            is RepoResult.Failure -> error = r.error.toMessage()
                        }
                    }
                }) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("إلغاء") } },
        )
    }
}
