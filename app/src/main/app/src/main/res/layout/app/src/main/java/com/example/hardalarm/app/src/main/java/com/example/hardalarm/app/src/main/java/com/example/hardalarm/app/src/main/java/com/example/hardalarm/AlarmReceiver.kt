package com.example.hardalarm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val phase = if (intent.action == AlarmScheduler.ACTION_PHASE_1) 1 else 2

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(AlarmService.CHANNEL_ID, "Будильник", NotificationManager.IMPORTANCE_HIGH)
        )

        val fullScreen = PendingIntent.getActivity(
            context, phase,
            Intent(context, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("phase", phase),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, AlarmService.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Жёсткий будильник")
            .setContentText(if (phase == 1) "Фаза 1 — просыпайся" else "Фаза 2 — реши примеры")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .build()

        if (Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(phase, notif)
        }

        AlarmService.start(context, phase)
    }
}
