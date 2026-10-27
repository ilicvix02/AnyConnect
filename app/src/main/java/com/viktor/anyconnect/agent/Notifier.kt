package com.viktor.anyconnect.agent

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object Notifier {
    private const val CHANNEL = "anyconnect_agent"
    private const val ID = 42

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = ctx.getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(CHANNEL, "AnyConnect agent", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }

    fun show(ctx: Context, text: String) {
        ensureChannel(ctx)
        val n = androidx.core.app.NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setContentTitle("AnyConnect")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        try {
            androidx.core.app.NotificationManagerCompat.from(ctx).notify(ID, n)
        } catch (_: SecurityException) {
            // notifications permission not granted; ignore
        }
    }

    fun clear(ctx: Context) {
        androidx.core.app.NotificationManagerCompat.from(ctx).cancel(ID)
    }
}
