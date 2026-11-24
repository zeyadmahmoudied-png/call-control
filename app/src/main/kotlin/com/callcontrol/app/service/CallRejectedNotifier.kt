package com.callcontrol.app.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.callcontrol.app.R

/** Posts a small local notification after a call was successfully rejected by Android. */
class CallRejectedNotifier(private val context: Context) {

    fun show(rawNumber: String?) {
        val number = rawNumber?.takeIf { it.isNotBlank() }
        val displayName = number?.let(::findContactName) ?: number ?: "رقم مخفي"
        val dialIntent = Intent(Intent.ACTION_DIAL).apply {
            data = number?.let { Uri.fromParts("tel", it, null) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val dialPendingIntent = PendingIntent.getActivity(
            context,
            (System.currentTimeMillis() and 0x7fffffff).toInt(),
            dialIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        createChannel()
        val compact = RemoteViews(context.packageName, R.layout.notification_rejected_call).apply {
            setTextViewText(R.id.notification_caller_name, displayName)
            setTextViewText(R.id.notification_caller_message, "لقد حاول الاتصال بك")
            setViewVisibility(R.id.notification_call_button, if (number == null) View.GONE else View.VISIBLE)
            setOnClickPendingIntent(R.id.notification_call_button, dialPendingIntent)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_call_missed)
            .setContentTitle(displayName)
            .setContentText("لقد حاول الاتصال بك")
            .setCustomContentView(compact)
            .setCustomBigContentView(compact)
            .setContentIntent(dialPendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            if (Build.VERSION.SDK_INT < 33 ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            ) {
                NotificationManagerCompat.from(context).notify(
                    (System.currentTimeMillis() and 0x7fffffff).toInt(),
                    notification,
                )
            }
        } catch (_: SecurityException) {
            // A denied notification permission must never affect call screening.
        }
    }

    private fun findContactName(number: String): String? {
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0)?.takeIf { it.isNotBlank() } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "المكالمات المرفوضة",
                        NotificationManager.IMPORTANCE_HIGH,
                    ).apply { description = "تنبيه عند رفض مكالمة واردة" },
                )
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "rejected_calls"
    }
}
