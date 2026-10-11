package com.angeset.dynamicisland

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Stores a small local snapshot of notification text for the island. */
class IslandNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.trim().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        getSharedPreferences("island_state", MODE_PRIVATE).edit()
            .putString("notification_title", title.take(100))
            .putString("notification_text", text.take(180))
            .putString("notification_package", sbn.packageName)
            .putLong("notification_time", sbn.postTime)
            .apply()
    }
}
