package com.callcontrol.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.callcontrol.app.MainViewModel
import com.callcontrol.app.Screen
import com.callcontrol.app.toMessage
import com.callcontrol.storage.RepoResult
import com.callcontrol.storage.ThemeMode

/** The whole app: theme, forced right-to-left layout (Arabic), start-up gates, and screen switching. */
@Composable
fun CallControlRoot(vm: MainViewModel = viewModel()) {
    val settingsState by vm.settings.collectAsStateWithLifecycle()
    val themeMode = (settingsState as? RepoResult.Success)?.value?.themeMode ?: ThemeMode.SYSTEM

    CallControlTheme(themeMode) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            val snackbar = remember { SnackbarHostState() }
            LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

            BackHandler(enabled = vm.backStack.size > 1) { vm.back() }

            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(modifier = Modifier.fillMaxSize()) {
                    when (val s = settingsState) {
                        null -> LoadingScreen()
                        is RepoResult.Failure -> StorageErrorScreen(s.error.toMessage(), onRetry = vm::retry)
                        is RepoResult.Success ->
                            if (!s.value.onboardingCompleted) OnboardingScreen(vm)
                            else ScreenHost(vm)
                    }
                    SnackbarHost(
                        hostState = snackbar,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ScreenHost(vm: MainViewModel) {
    AnimatedContent(
        targetState = vm.current,
        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
        label = "screens",
    ) { screen ->
        when (screen) {
            Screen.Home -> HomeScreen(vm)
            Screen.GlobalMode -> GlobalModeScreen(vm)
            Screen.Numbers -> NumbersScreen(vm)
            is Screen.EditNumber -> EditNumberScreen(vm, screen.id)
            Screen.Unregistered -> UnregisteredScreen(vm)
            Screen.Hidden -> HiddenScreen(vm)
            Screen.Schedule -> ScheduleScreen(vm)
            is Screen.EditEntry -> EditEntryScreen(vm, screen.id)
            Screen.Log -> LogScreen(vm)
            Screen.Settings -> SettingsScreen(vm)
            Screen.Diagnostics -> DiagnosticsScreen(vm)
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.testTag("loading"))
    }
}

@Composable
private fun StorageErrorScreen(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
            Text("تعذّر فتح بيانات التطبيق", style = MaterialTheme.typography.titleLarge)
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Text(
                "لن يمنع التطبيق أي مكالمة بسبب هذا الخطأ؛ تُسمح المكالمات حتى يعود التخزين للعمل.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRetry, modifier = Modifier.testTag("btn_retry")) { Text("إعادة المحاولة") }
        }
    }
}
