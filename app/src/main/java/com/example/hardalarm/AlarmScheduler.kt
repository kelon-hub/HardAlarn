package com.example.hardalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object AlarmScheduler {
    const val ACTION_PHASE_1 = "com.example.hardalarm.PHASE_1"
    const val ACTION_PHASE_2 = "com.example.hardalarm.PHASE_2"
    private const val PHASE2_OFFSET_MS = 2 * 60_000L

    fun schedule(context: Context, hour: Int, minute: Int, days: Set<Int> = emptySet()): Boolean {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) return false
        val selected = if (days.isEmpty()) (1..7).toSet() else days
        for (day in selected) {
            val t = nextOccurrenceOn(hour, minute, day)
            am.setAlarmClock(
                AlarmManager.AlarmClockInfo(t, openApp(context)),
                pi(context, 10 + day, ACTION_PHASE_1)
            )
            am.setAlarmClock(
                AlarmManager.AlarmClockInfo(t + PHASE2_OFFSET_MS, openApp(context)),
                pi(context, 20 + day, ACTION_PHASE_2)
            )
        }
        return true
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (day in 1..7) {
            am.cancel(pi(context, 10 + day, ACTION_PHASE_1))
            am.cancel(pi(context, 20 + day, ACTION_PHASE_2))
        }
    }

    fun nextOccurrenceOn(hour: Int, minute: Int, dayOfWeek: Int): Long =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            val diff = (dayOfWeek - get(Calendar.DAY_OF_WEEK) + 7) % 7
            if (diff > 0) add(Calendar.DAY_OF_YEAR, diff)
            else if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 7)
        }.timeInMillis

    private fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun pi(context: Context, code: Int, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, code,
            Intent(context, AlarmReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
