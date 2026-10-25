package app.bitmessenger.sms

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.app.Notification
import app.bitmessenger.R
import app.bitmessenger.ui.MainActivity

object Notifier {
    private const val CHANNEL = "messages"
    private const val MMS_ID = Int.MAX_VALUE // thread notifications use the thread id

    fun createChannels(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Messages", NotificationManager.IMPORTANCE_HIGH).apply { description = "New texts" },
        )
    }

    private fun allowed(context: Context) =
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun open(context: Context, threadId: Long?): PendingIntent = PendingIntent.getActivity(
        context, (threadId ?: 0L).toInt(),
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { threadId?.let { putExtra(MainActivity.EXTRA_THREAD, it) } },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun incoming(context: Context, threadId: Long, from: String, body: String) {
        if (!allowed(context)) return
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setContentTitle(from)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(open(context, threadId))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(threadId.toInt(), n)
    }

    fun mmsWaiting(context: Context) {
        if (!allowed(context)) return
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setContentTitle("Picture or group message waiting")
            .setContentText("bitmessenger can't download MMS yet. Open your old texting app to see it.")
            .setStyle(Notification.BigTextStyle().bigText("bitmessenger can't download picture or group (MMS) messages yet. Make your old texting app the default again to receive them."))
            .setContentIntent(open(context, null))
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(MMS_ID, n)
    }

    fun cancel(context: Context, threadId: Long) = context.getSystemService(NotificationManager::class.java).cancel(threadId.toInt())

    fun cancelAll(context: Context) = context.getSystemService(NotificationManager::class.java).cancelAll()
}
