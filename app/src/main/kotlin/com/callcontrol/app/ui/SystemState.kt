package com.callcontrol.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.callcontrol.app.system.SystemSnapshot
import com.callcontrol.app.system.SystemStatus

/** The system state, re-read every time the app comes back to the foreground (after a system dialog, for example). */
@Composable
fun rememberSystemSnapshot(): State<SystemSnapshot> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(SystemStatus.snapshot(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { state.value = SystemStatus.snapshot(context) }
    return state
}

/**
 * Returns a function that asks Android to make this app the call-screening app. It uses the official system dialog;
 * if that is unavailable it opens the "Default apps" settings so the user can choose it by hand.
 */
@Composable
fun rememberRoleRequester(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    return {
        val intent = SystemStatus.roleRequestIntent(context)
        if (intent != null) {
            try {
                launcher.launch(intent)
            } catch (e: ActivityNotFoundException) {
                openDefaultAppsSettings(context)
            }
        } else {
            openDefaultAppsSettings(context)
        }
    }
}

private fun openDefaultAppsSettings(context: Context) {
    try {
        context.startActivity(SystemStatus.defaultAppsSettingsIntent())
    } catch (e: ActivityNotFoundException) {
        // Nothing more can be done from inside the app; the diagnostics screen shows the manual steps.
    }
}

/** Returns a function that shows the system prompt for the optional contacts permission. */
@Composable
fun rememberContactsPermissionRequester(onResult: () -> Unit = {}): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onResult() }
    return { launcher.launch(android.Manifest.permission.READ_CONTACTS) }
}
