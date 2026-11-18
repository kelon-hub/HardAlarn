package com.example.hardalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("hard_alarm", Context.MODE_PRIVATE)
            if (prefs.getBoolean("enabled", false)) {
                val days = prefs.getStringSet("days", emptySet())?.map { it.toInt() }?.toSet() ?: emptySet()
                AlarmScheduler.schedule(
                    context,
                    prefs.getInt("hour", 5),
                    prefs.getInt("minute", 0),
                    days
                )
            }
        }
    }
}
