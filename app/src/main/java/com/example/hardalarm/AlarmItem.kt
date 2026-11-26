package com.example.hardalarm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class AlarmItem(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val days: Set<Int>,
    val enabled: Boolean,
    val musicUri: String? = null,
    val musicName: String = "Стандартная мелодия"
) {
    fun getNextTriggerMillis(): Long {
        val selected = if (days.isEmpty()) (1..7).toSet() else days
        return selected.minOf { day ->
            AlarmScheduler.nextOccurrenceOn(hour, minute, day)
        }
    }

    fun formatDays(): String {
        if (days.isEmpty() || days.size == 7) return "Каждый день"
        val names = mapOf(
            Calendar.MONDAY to "Пн",
            Calendar.TUESDAY to "Вт",
            Calendar.WEDNESDAY to "Ср",
            Calendar.THURSDAY to "Чт",
            Calendar.FRIDAY to "Пт",
            Calendar.SATURDAY to "Сб",
            Calendar.SUNDAY to "Вс"
        )
        val order = listOf(
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
            Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
        )
        return order.filter { days.contains(it) }.joinToString(", ") { names[it] ?: "" }
    }
}

object AlarmStorage {
    private const val PREFS_NAME = "hard_alarm"
    private const val KEY_LIST = "alarms_json_list"

    fun loadAll(context: Context): MutableList<AlarmItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_LIST, null) ?: return mutableListOf()
        val list = mutableListOf<AlarmItem>()
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val daysArr = obj.optJSONArray("days") ?: JSONArray()
                val daysSet = mutableSetOf<Int>()
                for (d in 0 until daysArr.length()) daysSet.add(daysArr.getInt(d))

                list.add(
                    AlarmItem(
                        id = obj.getInt("id"),
                        hour = obj.getInt("hour"),
                        minute = obj.getInt("minute"),
                        days = daysSet,
                        enabled = obj.optBoolean("enabled", true),
                        musicUri = obj.optString("musicUri", "").ifBlank { null },
                        musicName = obj.optString("musicName", "Стандартная мелодия")
                    )
                )
            }
        }
        return list
    }

    fun getById(context: Context, id: Int): AlarmItem? {
        return loadAll(context).find { it.id == id }
    }

    fun generateNextId(context: Context): Int {
        val maxId = loadAll(context).maxOfOrNull { it.id } ?: 0
        return maxId + 1
    }

    fun saveAll(context: Context, list: List<AlarmItem>) {
        val arr = JSONArray()
        list.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("hour", item.hour)
                put("minute", item.minute)
                put("days", JSONArray(item.days.toList()))
                put("enabled", item.enabled)
                put("musicUri", item.musicUri ?: "")
                put("musicName", item.musicName)
            }
            arr.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LIST, arr.toString())
            .apply()
    }

    fun addOrUpdate(context: Context, item: AlarmItem) {
        val list = loadAll(context)
        val idx = list.indexOfFirst { it.id == item.id }
        if (idx >= 0) list[idx] = item else list.add(item)
        saveAll(context, list)
    }

    fun delete(context: Context, id: Int) {
        val list = loadAll(context).filter { it.id != id }
        saveAll(context, list)
    }
}
