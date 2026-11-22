package com.example.hardalarm

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat

class AlarmsListActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout
    private lateinit var tvCountdown: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pixelFont = runCatching { ResourcesCompat.getFont(this, R.font.comic_sans_pixel) }.getOrNull()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0D0D12"))
            setPadding(44, 44, 44, 44)
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
            textSize = 14f
            typeface = pixelFont
            setPadding(0, 24, 0, 32)
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
        val sorted = AlarmStorage.loadSorted(this)
        val pixelFont = runCatching { ResourcesCompat.getFont(this, R.font.comic_sans_pixel) }.getOrNull()

        val nextActive = sorted.firstOrNull { it.enabled }
        if (nextActive != null) {
            val diffMs = (nextActive.getNextTriggerMillis() - System.currentTimeMillis()).coerceAtLeast(0)
            val totalMin = diffMs / 60_000
            val hours = totalMin / 60
            val mins = totalMin % 60
            tvCountdown.text = "Следующий сигнал через %d ч. %d мин. (%02d:%02d)".format(
                hours, mins, nextActive.hour, nextActive.minute
            )
        } else {
            tvCountdown.text = "Нет включённых будильников"
        }

        if (sorted.isEmpty()) {
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

        sorted.forEach { item ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(if (item.enabled) Color.parseColor("#181822") else Color.parseColor("#121217"))
                setPadding(28, 24, 28, 24)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = 16
                layoutParams = lp
            }

            val infoCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val tvTime = TextView(this).apply {
                text = "%02d:%02d".format(item.hour, item.minute)
                setTextColor(if (item.enabled) Color.parseColor("#00BFFF") else Color.GRAY)
                textSize = 28f
            }

            val tvDetails = TextView(this).apply {
                val notePart = if (item.noteText.isNotBlank()) " • ${item.noteText}" else ""
                val photoPart = if (item.photoUri != null) " [Фото]" else ""
                text = "${item.formatDays()}$notePart$photoPart"
                setTextColor(Color.LTGRAY)
                textSize = 13f
                typeface = pixelFont
            }

            infoCol.addView(tvTime)
            infoCol.addView(tvDetails)

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
                setOnClickListener {
                    AlarmScheduler.cancel(this@AlarmsListActivity, item.id)
                    AlarmStorage.delete(this@AlarmsListActivity, item.id)
                    renderAlarms()
                }
            }

            card.addView(infoCol)
            card.addView(sw)
            card.addView(btnDel)
            container.addView(card)
        }
    }
}
