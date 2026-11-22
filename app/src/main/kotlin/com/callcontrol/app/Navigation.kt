package com.callcontrol.app

/** Every screen of the app. [EditNumber] and [EditEntry] carry the stored id, or null when adding. */
sealed interface Screen {
    data object Home : Screen
    data object GlobalMode : Screen
    data object Numbers : Screen
    data class EditNumber(val id: Long?) : Screen
    data object Unregistered : Screen
    data object Hidden : Screen
    data object Schedule : Screen
    data class EditEntry(val id: Long?) : Screen
    data object Log : Screen
    data object Settings : Screen
    data object Diagnostics : Screen
}
