package com.example.hardalarm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra("alarm_id", -1)
        val alarmItem = if (alarmId > 0) AlarmStorage.getById(context, alarmId) else null

        // Если этот конкретный будильник выключен тумблером в списке — не звоним
        if (alarmItem != null && !alarmItem.enabled) return

        val phase = if (intent.action == AlarmScheduler.ACTION_PHASE_1) 1 else 2

        // 1. Принудительно будим экран телефона/планшета, даже если он выключен
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val wakeLock = pm.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or
            PowerManager.ACQUIRE_CAUSES_WAKEUP or
            PowerManager.ON_AFTER_RELEASE,
            "HardAlarm:WakeLock"
        )
        runCatching { wakeLock.acquire(15_000L) }

        // 2. Запускаем звук сразу в фоновом сервисе (пробивает блокировку экрана)
        AlarmService.start(context, phase, alarmId)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            "hard_alarm_fs", "Экран будильника", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setSound(null, null)
            enableVibration(true)
        }
        nm.createNotificationChannel(channel)

        val activityIntent = Intent(context, AlarmActivity::class.java)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            .setData(Uri.parse("hardalarm://ring/$alarmId/$phase"))
            .putExtra("phase", phase)
            .putExtra("alarm_id", alarmId)

        val fullScreen = PendingIntent.getActivity(
            context, alarmId * 10 + phase,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, "hard_alarm_fs")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("The Death Clocks")
            .setContentText(if (phase == 1) "Фаза 1 — просыпайся" else "Фаза 2 — реши 5 примеров")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .build()

        if (Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(100 + phase, notif)
        }

        runCatching { context.startActivity(activityIntent) }
    }
}
