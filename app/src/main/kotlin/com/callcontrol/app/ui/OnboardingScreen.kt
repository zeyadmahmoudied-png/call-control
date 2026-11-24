package com.callcontrol.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.callcontrol.app.MainViewModel

/** First launch: what the app does, what it cannot do, and how to switch the screening on. */
@Composable
fun OnboardingScreen(vm: MainViewModel) {
    val requestRole = rememberRoleRequester()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Icon(Icons.Filled.Call, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
        Text("التحكم في المكالمات", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "الوضع الافتراضي يرفض المكالمات الواردة، ويمكنك إضافة أرقام محددة إلى الاستثناءات للسماح لها بالاتصال.",
            style = MaterialTheme.typography.bodyLarge,
        )
        AppCard {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("قبل أن تبدأ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("• لكي يعمل التطبيق يجب أن تجعله «تطبيق تحديد هوية المتصل ومنع الإزعاج» من إعدادات أندرويد.", style = MaterialTheme.typography.bodyMedium)
                Text("• الرفض يتم من الهاتف. ما يسمعه المتصل (مشغول أو مغلق أو بريد صوتي) تحدده شركة الاتصالات ولا يستطيع التطبيق التحكم فيه.", style = MaterialTheme.typography.bodyMedium)
                Text("• كل بياناتك تبقى على هذا الهاتف فقط. لا إنترنت ولا حساب.", style = MaterialTheme.typography.bodyMedium)
                Text("• عند أي خطأ داخلي تُسمح المكالمة ولا تُمنع.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Button(
            onClick = {
                requestRole()
                vm.completeOnboarding()
            },
            modifier = Modifier.fillMaxWidth().testTag("btn_activate"),
        ) { Text("تفعيل الآن") }
        OutlinedButton(
            onClick = vm::completeOnboarding,
            modifier = Modifier.fillMaxWidth().testTag("btn_later"),
        ) { Text("لاحقًا") }
    }
}
