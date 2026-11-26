package com.example.hardalarm

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import java.util.Calendar

class AlarmsListActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout
    private lateinit var tvCountdown: TextView
    private var editingAlarmId: Int = -1

    private val pickAlarmMelodyLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null && editingAlarmId > 0) {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val fileName = getFileName(uri) ?: "Своя мелодия"
            val item = AlarmStorage.getById(this, editingAlarmId)
            if (item != null) {
                AlarmStorage.addOrUpdate(
                    this,
                    item.copy(musicUri = uri.toString(), musicName = fileName)
                )
                renderAlarms()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pixelFont = runCatching { ResourcesCompat.getFont(this, R.font.comic_sans_pixel) }.getOrNull()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0D0D12"))
            setPadding(40, 40, 40, 40)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleTv = TextView(this).apply {
            text = "Все будильники"
            setTextColor(Color.parseColor("#00BFFF"))
            textSize = 22f
            typeface = pixelFont
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnBack = Button(this).apply {
            text = "+ Новый"
            textSize = 14f
            typeface = pixelFont
            setOnClickListener { finish() }
        }

        header.addView(titleTv)
        header.addView(btnBack)

        tvCountdown = TextView(this).apply {
            text = "Следующий сигнал: не активен"
            setTextColor(Color.parseColor("#90CAF9"))
            textSize = 13f
            typeface = pixelFont
            setPadding(0, 20, 0, 24)
        }

        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            addView(container)
        }

        root.addView(header)
        root.addView(tvCountdown)
        root.addView(scroll)
        setContentView(root)

        renderAlarms()
    }

    private fun renderAlarms() {
        container.removeAllViews()
        val all = AlarmStorage.loadAll(this)
        val pixelFont = runCatching { ResourcesCompat.getFont(this, R.font.comic_sans_pixel) }.getOrNull()

        val nextActive = all.filter { it.enabled }.minByOrNull { it.getNextTriggerMillis() }
        if (nextActive != null) {
            val diffMs = (nextActive.getNextTriggerMillis() - System.currentTimeMillis()).coerceAtLeast(0)
            val totalMin = diffMs / 60_000
            val hours = totalMin / 60
            val mins = totalMin % 60
            tvCountdown.text = "Ближайший через %d ч. %d мин. (%02d:%02d)".format(
                hours, mins, nextActive.hour, nextActive.minute
            )
        } else {
            tvCountdown.text = "Нет включённых будильников"
        }

        if (all.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "Список пуст. Нажми «+ Новый», чтобы добавить будильник."
                setTextColor(Color.GRAY)
                textSize = 15f
                typeface = pixelFont
                setPadding(16, 32, 16, 16)
            }
            container.addView(emptyTv)
            return
        }

        // Группировка по дням недели (Пн–Вс) с сортировкой по времени (ЧЧ:ММ) внутри каждого дня
        val orderedDays = listOf(
            Calendar.MONDAY to "Понедельник",
            Calendar.TUESDAY to "Вторник",
            Calendar.WEDNESDAY to "Среда",
            Calendar.THURSDAY to "Четверг",
            Calendar.FRIDAY to "Пятница",
            Calendar.SATURDAY to "Суббота",
            Calendar.SUNDAY to "Воскресенье"
        )

        orderedDays.forEach { (dayCode, dayTitle) ->
            val dayAlarms = all.filter { it.days.isEmpty() || it.days.contains(dayCode) }
                .sortedWith(compareBy({ it.hour }, { it.minute }))

            if (dayAlarms.isNotEmpty()) {
                val dayHeader = TextView(this).apply {
                    text = dayTitle
                    setTextColor(Color.parseColor("#00BFFF"))
                    textSize = 16f
                    typeface = pixelFont
                    setPadding(8, 24, 8, 10)
                }
                container.addView(dayHeader)

                dayAlarms.forEach { item ->
                    container.addView(createAlarmCard(item, pixelFont))
                }
            }
        }
    }

    private fun createAlarmCard(item: AlarmItem, pixelFont: android.graphics.Typeface?): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(if (item.enabled) Color.parseColor("#181822") else Color.parseColor("#121217"))
            setPadding(24, 20, 20, 20)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = 12
            layoutParams = lp
        }

        val infoCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvTime = TextView(this).apply {
            text = "%02d:%02d".format(item.hour, item.minute)
            setTextColor(if (item.enabled) Color.WHITE else Color.GRAY)
            textSize = 26f
        }

        val tvMelody = TextView(this).apply {
            text = "♪ ${item.musicName}"
            setTextColor(Color.parseColor("#90CAF9"))
            textSize = 12f
            typeface = pixelFont
        }

        infoCol.addView(tvTime)
        infoCol.addView(tvMelody)

        // Кнопка выбора отдельной мелодии именно для этого будильника
        val btnMelody = Button(this).apply {
            text = "♪"
            setTextColor(Color.parseColor("#00BFFF"))
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(100, LinearLayout.LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                editingAlarmId = item.id
                pickAlarmMelodyLauncher.launch(arrayOf("audio/*"))
            }
        }

        val sw = Switch(this).apply {
            isChecked = item.enabled
            setOnCheckedChangeListener { _, isChecked ->
                val updated = item.copy(enabled = isChecked)
                AlarmStorage.addOrUpdate(this@AlarmsListActivity, updated)
                if (isChecked) {
                    AlarmScheduler.schedule(this@AlarmsListActivity, item.hour, item.minute, item.days, item.id)
                } else {
                    AlarmScheduler.cancel(this@AlarmsListActivity, item.id)
                }
                renderAlarms()
            }
        }

        val btnDel = Button(this).apply {
            text = "✕"
            setTextColor(Color.parseColor("#FF5252"))
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(100, LinearLayout.LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                AlarmScheduler.cancel(this@AlarmsListActivity, item.id)
                AlarmStorage.delete(this@AlarmsListActivity, item.id)
                renderAlarms()
            }
        }

        card.addView(infoCol)
        card.addView(btnMelody)
        card.addView(sw)
        card.addView(btnDel)
        return card
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) result = cursor.getString(idx)
                }
            }
        }
        return result ?: uri.lastPathSegment
    }
}
