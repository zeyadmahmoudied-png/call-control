package com.callcontrol.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callcontrol.app.MainViewModel
import com.callcontrol.storage.AppSettings
import com.callcontrol.storage.RepoResult

@Composable
private fun currentSettings(vm: MainViewModel): AppSettings {
    val state by vm.settings.collectAsStateWithLifecycle()
    return (state as? RepoResult.Success)?.value ?: AppSettings()
}

@Composable
private fun ModeScreenBody(padding: PaddingValues, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

/** Global mode: what happens to every call that no more specific rule decides. */
@Composable
fun GlobalModeScreen(vm: MainViewModel) {
    val settings = currentSettings(vm)
    ScreenScaffold(title = "الوضع العام", onBack = { vm.back() }) { padding ->
        ModeScreenBody(padding) {
            Notice("الوضع العام هو الحالة الافتراضية لكل مكالمة لا تنطبق عليها قاعدة رقم محدد أو قاعدة غير المسجل أو المخفي.")
            AppCard {
                SwitchRow(
                    title = "تشغيل التحكم",
                    subtitle = "إيقافه يسمح بكل المكالمات دون حذف أي إعداد.",
                    checked = settings.controlEnabled,
                    onCheckedChange = { on -> vm.updateSettings { it.copy(controlEnabled = on) } },
                    tag = "switch_control_global",
                )
            }
            if (settings.scheduleEnabled) {
                Notice("الجدولة مفعّلة: الوضع الفعلي يتغير حسب الجدول. الوضع المختار هنا يُستخدم خارج فترات الجدول فقط إن لم تحدد وضعًا لباقي اليوم.")
            }
            ModeSelector(
                selected = settings.globalMode,
                onSelect = { m -> vm.updateSettings { it.copy(globalMode = m) } },
                tagPrefix = "global_mode",
            )
        }
    }
}

/** Numbers that are not saved in the user's contacts. Independent of the hidden-number rule. */
@Composable
fun UnregisteredScreen(vm: MainViewModel) {
    val settings = currentSettings(vm)
    val system by rememberSystemSnapshot()
    val askPermission = rememberContactsPermissionRequester()
    ScreenScaffold(title = "الأرقام غير المسجلة", onBack = { vm.back() }) { padding ->
        ModeScreenBody(padding) {
            Notice("هي المكالمات التي يظهر فيها رقم المتصل لكنه غير محفوظ في جهات اتصالك. المكالمات التي لا يظهر فيها رقم أصلًا (مخفية) لها إعداد منفصل.")
            if (!system.contactsGranted) {
                Notice(
                    text = "هذه القاعدة تحتاج إذن قراءة جهات الاتصال لمعرفة هل الرقم محفوظ. بدون الإذن لا تُطبَّق القاعدة أبدًا، ولن يُرفض أحد بالخطأ.",
                    error = true,
                    modifier = Modifier.testTag("notice_contacts_permission"),
                ) {
                    Button(onClick = askPermission, modifier = Modifier.testTag("btn_grant_contacts")) { Text("السماح بقراءة جهات الاتصال") }
                }
            }
            AppCard {
                SwitchRow(
                    title = "تطبيق قاعدة الأرقام غير المسجلة",
                    subtitle = if (settings.unregistered.enabled) "مفعّلة" else "غير مفعّلة: تُعامل هذه المكالمات حسب الوضع العام.",
                    checked = settings.unregistered.enabled,
                    onCheckedChange = { on -> vm.updateSettings { it.copy(unregistered = it.unregistered.copy(enabled = on)) } },
                    tag = "switch_unregistered",
                )
            }
            ModeSelector(
                selected = settings.unregistered.mode,
                onSelect = { m -> vm.updateSettings { it.copy(unregistered = it.unregistered.copy(mode = m)) } },
                tagPrefix = "unregistered_mode",
            )
        }
    }
}

/** Calls without a visible number. Independent of the unregistered rule. */
@Composable
fun HiddenScreen(vm: MainViewModel) {
    val settings = currentSettings(vm)
    ScreenScaffold(title = "الأرقام المخفية", onBack = { vm.back() }) { padding ->
        ModeScreenBody(padding) {
            Notice("هي المكالمات التي يُخفي فيها المتصل رقمه فلا يظهر لدينا رقم. تختلف عن «غير المسجل» الذي يظهر رقمه لكنه غير محفوظ عندك. لا يمكن وضع قاعدة لرقم محدد على مكالمة مخفية لأنه لا يوجد رقم.")
            AppCard {
                SwitchRow(
                    title = "تطبيق قاعدة الأرقام المخفية",
                    subtitle = if (settings.hidden.enabled) "مفعّلة" else "غير مفعّلة: تُعامل هذه المكالمات حسب الوضع العام.",
                    checked = settings.hidden.enabled,
                    onCheckedChange = { on -> vm.updateSettings { it.copy(hidden = it.hidden.copy(enabled = on)) } },
                    tag = "switch_hidden",
                )
            }
            ModeSelector(
                selected = settings.hidden.mode,
                onSelect = { m -> vm.updateSettings { it.copy(hidden = it.hidden.copy(mode = m)) } },
                tagPrefix = "hidden_mode",
            )
        }
    }
}
