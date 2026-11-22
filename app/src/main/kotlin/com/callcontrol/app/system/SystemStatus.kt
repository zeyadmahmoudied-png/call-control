package com.callcontrol.app.system

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

/** What the system currently allows this app to do. Read-only snapshot; re-read on resume. */
data class SystemSnapshot(
    /** The device offers the call-screening role at all. */
    val roleAvailable: Boolean,
    /** This app is the call-screening app (the service is bound by Android only then). */
    val roleHeld: Boolean,
    val contactsGranted: Boolean,
)

object SystemStatus {

    fun snapshot(context: Context): SystemSnapshot {
        val roles = context.getSystemService(RoleManager::class.java)
        val available = roles != null && roles.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)
        return SystemSnapshot(
            roleAvailable = available,
            roleHeld = available && roles.isRoleHeld(RoleManager.ROLE_CALL_SCREENING),
            contactsGranted = context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED,
        )
    }

    /** The official system dialog asking the user to make this app the call-screening app. Null if unavailable. */
    fun roleRequestIntent(context: Context): Intent? {
        val roles = context.getSystemService(RoleManager::class.java) ?: return null
        if (!roles.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) return null
        return roles.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
    }

    /** Manual route when the dialog is not available: Settings > Apps > Default apps. */
    fun defaultAppsSettingsIntent(): Intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
}
