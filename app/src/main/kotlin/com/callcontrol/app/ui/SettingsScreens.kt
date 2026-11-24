package com.callcontrol.app.ui

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callcontrol.app.MainViewModel
import com.callcontrol.app.Screen
import com.callcontrol.app.label
import com.callcontrol.app.toMessage
import com.callcontrol.storage.AppSettings
import com.callcontrol.storage.RepoResult
import com.callcontrol.storage.ThemeMode

private fun versionText(context: Context): String = try {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    @Suppress("DEPRECATION")
    "${info.versionName} (${info.longVersionCode})"
} catch (e: PackageManager.NameNotFoundException) {
    "غير معروف"
}

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val state by vm.settings.collectAsStateWithLifecycle()
    val settings = (state as? RepoResult.Success)?.value ?: AppSettings()
    val context = LocalContext.current

    ScreenScaffold(title = "الإعدادات", onBack = { vm.back() }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("المظهر")
            AppCard {
                Column {
                    ThemeMode.values().forEach { t ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = settings.themeMode == t, role = Role.RadioButton) {
                                    vm.updateSettings { it.copy(themeMode = t) }
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .testTag("theme_${t.name}"),
                        ) {
                            RadioButton(selected = settings.themeMode == t, onClick = null)
                            Text(t.label(), modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            }
            SectionTitle("السجل")
            AppCard {
                SwitchRow(
                    title = "حفظ سجل المكالمات",
                    subtitle = "محلي على الهاتف فقط. لا يُحفظ رقم المتصل المخفي.",
                    checked = settings.logEnabled,
                    onCheckedChange = { on -> vm.updateSettings { it.copy(logEnabled = on) } },
                    tag = "switch_log",
                )
            }
            OutlinedButton(onClick = { vm.clearLog() }, modifier = Modifier.fillMaxWidth().testTag("btn_clear_log_settings")) { Text("مسح السجل الآن") }
            SectionTitle("أدوات")
            OutlinedButton(onClick = { vm.navigate(Screen.Diagnostics) }, modifier = Modifier.fillMaxWidth().testTag("btn_open_diagnostics")) {
                Text("حالة التطبيق والأذونات")
            }
            SectionTitle("قواعد إضافية")
            Text(
                "تُستخدم هذه القواعد عندما يكون الوضع العام «متاح» أو «صامت». وضع الرفض العام يتجاوزها حتى لا تمر مكالمة دون استثناء رقم محدد.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { vm.navigate(Screen.Unregistered) }, modifier = Modifier.fillMaxWidth().testTag("nav_unregistered")) {
                Text("الأرقام غير المسجلة")
            }
            OutlinedButton(onClick = { vm.navigate(Screen.Hidden) }, modifier = Modifier.fillMaxWidth().testTag("nav_hidden")) {
                Text("الأرقام المخفية")
            }
            OutlinedButton(onClick = { vm.navigate(Screen.Schedule) }, modifier = Modifier.fillMaxWidth().testTag("nav_schedule")) {
                Text("الجدولة")
            }
            SectionTitle("عن التطبيق")
            Text("الإصدار: ${versionText(context)}", modifier = Modifier.testTag("text_version"))
            Text(
                "يعمل بالكامل دون إنترنت ولا يملك إذن الاتصال بالشبكة.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun DiagnosticsScreen(vm: MainViewModel) {
    val system by rememberSystemSnapshot()
    val requestRole = rememberRoleRequester()
    val requestContacts = rememberContactsPermissionRequester()
    val logState by vm.log.collectAsStateWithLifecycle()
    val last = (logState as? RepoResult.Success)?.value?.firstOrNull()
    var storage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        storage = when (val r = vm.checkStorage()) {
            is RepoResult.Success -> "سليم: تُقرأ القواعد والإعدادات بنجاح."
            is RepoResult.Failure -> "يوجد خطأ: ${r.error.toMessage()}"
        }
    }

    ScreenScaffold(title = "حالة التطبيق", onBack = { vm.back() }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("التحكم في المكالمات")
            AppCard {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        when {
                            !system.roleAvailable -> "هذا الجهاز لا يوفّر دور «تطبيق فرز المكالمات»، فلا يمكن للتطبيق التحكم في المكالمات عليه."
                            system.roleHeld -> "مفعّل: التطبيق هو تطبيق فرز المكالمات."
                            else -> "غير مفعّل: لن يُطبَّق أي تحكم قبل تفعيل التطبيق كتطبيق فرز المكالمات."
                        },
                        modifier = Modifier.testTag("diag_role"),
                    )
                    if (system.roleAvailable && !system.roleHeld) {
                        Button(onClick = requestRole, modifier = Modifier.fillMaxWidth().testTag("btn_enable_service")) { Text("تفعيل التحكم") }
                        Text(
                            "إن لم تظهر نافذة التفعيل: الإعدادات ← التطبيقات ← التطبيقات الافتراضية ← تطبيق معرّف المتصل والبريد المزعج ← اختر «التحكم في المكالمات».",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            SectionTitle("إذن جهات الاتصال (اختياري)")
            AppCard {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (system.contactsGranted) "ممنوح: يمكن تمييز الأرقام غير المسجلة."
                        else "غير ممنوح: قاعدة «غير المسجل» لا تُطبَّق، ولا يُرفض أحد بسببها.",
                        modifier = Modifier.testTag("diag_contacts"),
                    )
                    if (!system.contactsGranted) {
                        Button(onClick = requestContacts, modifier = Modifier.fillMaxWidth().testTag("btn_grant_contacts")) { Text("السماح") }
                    }
                }
            }
            SectionTitle("التخزين")
            AppCard { Text(storage ?: "جارٍ الفحص…", modifier = Modifier.padding(14.dp).testTag("diag_storage")) }
            SectionTitle("آخر قرار")
            AppCard {
                Text(
                    last?.let { "${formatLogTime(it.timestampMillis)} — ${it.mode.label()} (${it.reason.label()})" }
                        ?: "لم تُعالَج أي مكالمة بعد (أو السجل متوقف).",
                    modifier = Modifier.padding(14.dp).testTag("diag_last"),
                )
            }
            SectionTitle("حدود لا يتحكم فيها التطبيق")
            Notice(
                "«مشغول» و«غير متاح» يرفضان المكالمة من هاتفك بنفس الطريقة. ما يسمعه المتصل بعد الرفض (نغمة مشغول، رسالة، بريد صوتي) تحدده شبكة المشغّل ولا يستطيع التطبيق تغييره، ولا يمكنه جعل الشبكة تعلن أن هاتفك مغلق أو خارج التغطية. لم نختبر سلوك شبكة e& مع الرفض.",
            )
        }
    }
}
