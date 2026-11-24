package dev.macbuildssys.consentguard

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

internal object ConsentReadyNotification {
    private const val CHANNEL = "consent_review_ready"
    private const val WARNING_CHANNEL = "consent_review_scroll_warning"
    private const val ID = 1
    private const val WARNING_ID = 2

    fun show(context: Context, durationMs: Long) {
        val notifications = enabledManager(context) ?: return

        val localized = context.forAppLanguage()
        createChannel(localized)
        val duration = localized.reviewDurationText(durationMs)
        val notification = NotificationCompat.Builder(localized, CHANNEL)
            .setSmallIcon(R.drawable.ic_completion_bell)
            .setContentTitle(localized.getString(R.string.vendor_switches_off_title))
            .setContentText(duration)
            .setStyle(NotificationCompat.BigTextStyle().bigText(duration))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setDefaults(NotificationCompat.DEFAULT_SOUND)
            .setTimeoutAfter(5 * 60_000L)
            .build()
        post(notifications, ID, notification)
    }

    fun showScrollDelayWarning(context: Context): Boolean {
        val notifications = enabledManager(context) ?: return false
        val localized = context.forAppLanguage()
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                WARNING_CHANNEL,
                localized.getString(R.string.review_scroll_delay),
                NotificationManager.IMPORTANCE_HIGH
            ).apply { setShowBadge(false) }
            manager.createNotificationChannel(channel)
            if (manager.getNotificationChannel(WARNING_CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) {
                return false
            }
        }
        val openSettings = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_SCROLL_DELAY)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val contentIntent = PendingIntent.getActivity(context, WARNING_ID, openSettings, PendingIntent.FLAG_IMMUTABLE)
        val warning = localized.getString(R.string.scroll_delay_review_toast)
        val notification = NotificationCompat.Builder(localized, WARNING_CHANNEL)
            .setSmallIcon(R.drawable.ic_completion_bell)
            .setContentTitle(localized.getString(R.string.review_scroll_delay))
            .setContentText(warning)
            .setStyle(NotificationCompat.BigTextStyle().bigText(warning))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_SOUND)
            .build()
        return post(notifications, WARNING_ID, notification)
    }

    private fun enabledManager(context: Context): NotificationManagerCompat? {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return null
        return NotificationManagerCompat.from(context).takeIf { it.areNotificationsEnabled() }
    }

    private fun post(manager: NotificationManagerCompat, id: Int, notification: Notification): Boolean = try {
        manager.notify(id, notification)
        true
    } catch (_: SecurityException) {
        false
    }

    fun clear(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID)
    }

    fun clearScrollDelayWarning(context: Context) {
        NotificationManagerCompat.from(context).cancel(WARNING_ID)
    }

    fun settingsIntent(context: Context): Intent {
        if (Build.VERSION.SDK_INT < 26) return appDetailsIntent(context)
        createChannel(context.forAppLanguage())
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL)
        }
        return Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }

    fun appDetailsIntent(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val channel = NotificationChannel(
            CHANNEL, context.getString(R.string.completion_alerts), NotificationManager.IMPORTANCE_DEFAULT
        ).apply { setShowBadge(false) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
