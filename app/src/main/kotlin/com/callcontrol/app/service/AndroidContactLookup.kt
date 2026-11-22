package com.callcontrol.app.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import com.callcontrol.domain.ContactStatus
import com.callcontrol.storage.ContactLookup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Asks Android's contacts provider whether a number belongs to a saved contact.
 *
 * Without READ_CONTACTS the answer is UNKNOWN (never NOT_IN_CONTACTS), so a missing permission can never make
 * the "unregistered numbers" rule fire for everybody.
 */
class AndroidContactLookup(private val context: Context) : ContactLookup {

    override suspend fun lookup(rawNumber: String): ContactStatus = withContext(Dispatchers.IO) {
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return@withContext ContactStatus.UNKNOWN
        }
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(rawNumber))
        val cursor = context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)
            ?: return@withContext ContactStatus.UNKNOWN // provider not available
        cursor.use { if (it.moveToFirst()) ContactStatus.IN_CONTACTS else ContactStatus.NOT_IN_CONTACTS }
    }
}
