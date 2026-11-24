package com.callcontrol.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callcontrol.app.MainViewModel
import com.callcontrol.app.Screen
import com.callcontrol.domain.Mode
import com.callcontrol.storage.AppSettings
import com.callcontrol.storage.RepoResult

@Composable
fun HomeScreen(vm: MainViewModel) {
    val settingsState by vm.settings.collectAsStateWithLifecycle()
    val rulesState by vm.rules.collectAsStateWithLifecycle()
    val system by rememberSystemSnapshot()
    val requestRole = rememberRoleRequester()

    val settings: AppSettings = (settingsState as? RepoResult.Success)?.value ?: AppSettings()
    val rules = (rulesState as? RepoResult.Success)?.value.orEmpty()
    val enabledExceptions = rules.count { it.enabled }

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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!system.roleHeld) {
                Notice(
                    text = if (system.roleAvailable) {
                        "التطبيق غير مفعّل كخدمة فحص المكالمات، لذلك لن يستطيع رفض المكالمات الآن."
                    } else {
                        "هذا الجهاز لا يدعم خدمة فحص المكالمات."
                    },
                    error = true,
                    modifier = Modifier.testTag("notice_service_off"),
                ) {
                    if (system.roleAvailable) {
                        Button(onClick = requestRole, modifier = Modifier.testTag("btn_enable_service")) {
                            Text("تفعيل الخدمة")
                        }
                    }
                }
            }

            // Main card: one obvious place to choose the default action and turn screening on/off.
            AppCard {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.navigate(Screen.GlobalMode) }
                            .padding(16.dp)
                            .testTag("nav_global"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("الوضع العام", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                when (settings.globalMode) {
                                    Mode.BUSY, Mode.UNAVAILABLE -> "رفض كل المكالمات ما عدا الاستثناءات"
                                    Mode.AVAILABLE -> "السماح بالمكالمات"
                                    Mode.SILENT -> "السماح بالمكالمات مع كتم الرنين"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        ModeChip(settings.globalMode)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                    }
                    SwitchRow(
                        title = "تفعيل التحكم",
                        subtitle = if (settings.controlEnabled) "الحظر يعمل حسب الوضع العام والاستثناءات." else "متوقف؛ ستصل المكالمات بشكل طبيعي.",
                        checked = settings.controlEnabled,
                        onCheckedChange = { enabled -> vm.updateSettings { it.copy(controlEnabled = enabled) } },
                        tag = "switch_control",
                    )
                }
            }

            // Exceptions are deliberately the only secondary management card on the home screen.
            AppCard {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f).clickable { vm.navigate(Screen.Numbers) }.testTag("nav_numbers"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Contacts, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text("الاستثناءات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (enabledExceptions == 0) "أضف الأرقام المسموح لها بالوصول إليك"
                                else "${enabledExceptions} رقمًا له إعداد خاص",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                    }
                    IconButton(
                        onClick = { vm.navigate(Screen.EditNumber(null)) },
                        modifier = Modifier.testTag("btn_add_number"),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "إضافة استثناء", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            if (settings.scheduleEnabled) {
                Notice(if (settings.globalMode.action == com.callcontrol.domain.CallAction.REJECT) "الجدولة مفعّلة، لكنها لا تتجاوز الرفض العام." else "الجدولة مفعّلة وقد تغيّر الوضع العام في أوقات محددة.")
            }

            AppCard(
                onClick = { vm.navigate(Screen.Log) },
                modifier = Modifier.testTag("nav_log"),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("سجل المكالمات", modifier = Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.titleMedium)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}
