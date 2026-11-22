package com.callcontrol.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callcontrol.app.MainViewModel
import com.callcontrol.app.Screen
import com.callcontrol.app.displayNumber
import com.callcontrol.app.label
import com.callcontrol.app.ltr
import com.callcontrol.domain.Mode
import com.callcontrol.domain.ScheduleResolver
import com.callcontrol.storage.AppSettings
import com.callcontrol.storage.RepoResult
import com.callcontrol.storage.buildRuleConfig
import java.time.LocalDateTime

@Composable
fun HomeScreen(vm: MainViewModel) {
    val settingsState by vm.settings.collectAsStateWithLifecycle()
    val rulesState by vm.rules.collectAsStateWithLifecycle()
    val entriesState by vm.entries.collectAsStateWithLifecycle()
    val logState by vm.log.collectAsStateWithLifecycle()
    val system by rememberSystemSnapshot()
    val requestRole = rememberRoleRequester()

    val settings: AppSettings = (settingsState as? RepoResult.Success)?.value ?: AppSettings()
    val rules = (rulesState as? RepoResult.Success)?.value.orEmpty()
    val entries = (entriesState as? RepoResult.Success)?.value.orEmpty()
    val recent = (logState as? RepoResult.Success)?.value.orEmpty().take(3)

    // The mode that applies to "everybody else" right now (manual mode, or what the schedule says at this moment).
    val effective = ScheduleResolver.resolve(
        buildRuleConfig(rules, entries, settings).schedule, LocalDateTime.now(), settings.globalMode,
    ).mode

    ScreenScaffold(
        title = "التحكم في المكالمات",
        onBack = null,
        actions = {
            IconButton(onClick = { vm.navigate(Screen.Settings) }, modifier = Modifier.testTag("btn_settings")) {
                Icon(Icons.Filled.Settings, contentDescription = "الإعدادات")
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
            // --- is the screening service actually switched on in Android? ---
            if (!system.roleHeld) {
                Notice(
                    text = if (system.roleAvailable) {
                        "خدمة فحص المكالمات غير مفعّلة في أندرويد، لذلك لا يُطبَّق أي تحكم الآن."
                    } else {
                        "هذا الجهاز لا يوفّر خدمة فحص المكالمات، لذلك لا يمكن للتطبيق التحكم في المكالمات."
                    },
                    error = true,
                    modifier = Modifier.testTag("notice_service_off"),
                ) {
                    if (system.roleAvailable) {
                        Button(onClick = requestRole, modifier = Modifier.testTag("btn_enable_service")) { Text("تفعيل الخدمة") }
                    }
                }
            }

            // --- master switch + the state that applies right now ---
            AppCard {
                Column {
                    SwitchRow(
                        title = "التحكم في المكالمات",
                        subtitle = if (settings.controlEnabled) "مفعّل: تُطبَّق قواعدك على المكالمات الواردة." else "متوقف: تصل كل المكالمات بشكل طبيعي. إعداداتك محفوظة.",
                        checked = settings.controlEnabled,
                        onCheckedChange = { on -> vm.updateSettings { it.copy(controlEnabled = on) } },
                        tag = "switch_control",
                    )
                    if (settings.controlEnabled) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                        ) {
                            Text("الحالة الآن لبقية المتصلين:", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            ModeChip(effective, modifier = Modifier.testTag("chip_effective_mode"))
                        }
                    }
                }
            }

            Button(
                onClick = { vm.navigate(Screen.EditNumber(null)) },
                modifier = Modifier.fillMaxWidth().testTag("btn_add_number"),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("إضافة رقم")
            }

            SectionTitle("القواعد (بالترتيب: رقم محدد ← غير مسجل ← مخفي ← الوضع العام)")

            NavCard(Icons.Filled.Tune, "الوضع العام", settings.globalMode.label(), "nav_global", mode = settings.globalMode) { vm.navigate(Screen.GlobalMode) }
            NavCard(Icons.Filled.Contacts, "الأرقام الخاصة", "${rules.size} قاعدة", "nav_numbers") { vm.navigate(Screen.Numbers) }
            NavCard(
                Icons.Filled.PersonOff, "الأرقام غير المسجلة",
                if (settings.unregistered.enabled) settings.unregistered.mode.label() else "غير مفعّل", "nav_unregistered",
                mode = settings.unregistered.mode.takeIf { settings.unregistered.enabled },
            ) { vm.navigate(Screen.Unregistered) }
            NavCard(
                Icons.Filled.VisibilityOff, "الأرقام المخفية",
                if (settings.hidden.enabled) settings.hidden.mode.label() else "غير مفعّل", "nav_hidden",
                mode = settings.hidden.mode.takeIf { settings.hidden.enabled },
            ) { vm.navigate(Screen.Hidden) }
            NavCard(
                Icons.Filled.Schedule, "الجدولة",
                if (settings.scheduleEnabled) "مفعّلة (${entries.size} فترة)" else "غير مفعّلة", "nav_schedule",
            ) { vm.navigate(Screen.Schedule) }

            SectionTitle("آخر المكالمات التي عالجها التطبيق")
            AppCard(onClick = { vm.navigate(Screen.Log) }, modifier = Modifier.testTag("card_recent")) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (recent.isEmpty()) {
                        Text("لا توجد مكالمات مسجلة بعد.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        recent.forEach { e ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(ltr(displayNumber(e.numberKey)), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                ModeChip(e.mode)
                            }
                        }
                    }
                }
            }

            NavCard(Icons.Filled.History, "سجل المكالمات", "كل ما عالجه التطبيق", "nav_log") { vm.navigate(Screen.Log) }
            NavCard(Icons.Filled.Security, "الصلاحيات والتشخيص", if (system.roleHeld) "الخدمة مفعّلة" else "الخدمة غير مفعّلة", "nav_diagnostics") { vm.navigate(Screen.Diagnostics) }
        }
    }
}

@Composable
private fun NavCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tag: String,
    mode: Mode? = null,
    onClick: () -> Unit,
) {
    AppCard(onClick = onClick, modifier = Modifier.testTag(tag)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (mode != null) {
                ModeChip(mode)
                Spacer(Modifier.width(8.dp))
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}
