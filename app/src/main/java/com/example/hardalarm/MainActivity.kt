package com.example.hardalarm

import android.Manifest
import android.app.AlarmManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.TimePicker
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var info: TextView
    private val selectedDays = mutableSetOf<Int>()
    private var currentMusicUri: String? = null

    private val pickAudioLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            currentMusicUri = uri.toString()
            getSharedPreferences("hard_alarm", Context.MODE_PRIVATE)
                .edit()
                .putString("music_uri", uri.toString())
                .apply()
            info.text = "Своя мелодия выбрана для этого будильника!"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences("hard_alarm", Context.MODE_PRIVATE)
        val picker = findViewById<TimePicker>(R.id.timePicker)
        picker.setIs24HourView(true)
        picker.hour = 8
        picker.minute = 0
        info = findViewById(R.id.info)

        // Треугольник: сообщение и фото при срабатывании
        findViewById<View>(R.id.rowMessage).setOnClickListener {
            startActivity(Intent(this, GoalsActivity::class.java))
        }

        // Нота: выбор мелодии
        findViewById<View>(R.id.rowMelody).setOnClickListener {
            pickAudioLauncher.launch(arrayOf("audio/*"))
        }

        // Кнопка СПРАВА: открыть полноценный список "Все будильники" с сортировкой по времени
        findViewById<Button>(R.id.btnAllAlarms).setOnClickListener {
            startActivity(Intent(this, AlarmsListActivity::class.java))
        }

        // Кнопка СЛЕВА: настройки самих будильников (дни недели + Ночь/Рассвет)
        findViewById<ImageButton>(R.id.btnAlarmSettings).setOnClickListener {
            showAlarmSettingsDialog()
        }

        // Кнопка ПО ЦЕНТРУ: сохранить новый будильник в список и поставить в расписание
        findViewById<Button>(R.id.btnSet).setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }

            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                info.text = "Дай разрешение на точные будильники и нажми ещё раз"
                startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
                )
                return@setOnClickListener
            }

            // Генерируем уникальный ID для нового будильника, чтобы он не затирал старые
            val newId = ((System.currentTimeMillis() / 1000) % 50_000).toInt().coerceAtLeast(1)
            val noteText = prefs.getString("note_text", "") ?: ""
            val photoUri = prefs.getString("note_photo_uri", null)
            val musicUri = currentMusicUri ?: prefs.getString("music_uri", null)

            val newAlarm = AlarmItem(
                id = newId,
                hour = picker.hour,
                minute = picker.minute,
                days = selectedDays.toSet(),
                enabled = true,
                noteText = noteText,
                photoUri = photoUri,
                musicUri = musicUri
            )

            val ok = AlarmScheduler.schedule(this, picker.hour, picker.minute, selectedDays, newId)
            if (ok) {
                AlarmStorage.addOrUpdate(this, newAlarm)
                val totalCount = AlarmStorage.loadAll(this).size
                info.text = "Добавлен на %02d:%02d! Всего будильников: $totalCount".format(picker.hour, picker.minute)
                // Сбрасываем выбранные дни для следующего нового будильника
                selectedDays.clear()
            } else {
                info.text = "Не удалось поставить будильник — проверь права"
            }
        }

        // Заглушки для нижних вкладок (Таймер, Секундомер, Мировые часы, Погода)
        val stubTabs = listOf(R.id.tabTimer, R.id.tabStopwatch, R.id.tabWorldClock, R.id.tabWeather)
        stubTabs.forEach { tabId ->
            findViewById<Button>(tabId).setOnClickListener { btn ->
                info.text = "Раздел «${(btn as Button).text}» скоро появится!"
            }
        }
    }

    private fun showAlarmSettingsDialog() {
        val prefs = getSharedPreferences("hard_alarm", Context.MODE_PRIVATE)
        val dayNames = arrayOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
        val dayCodes = intArrayOf(
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
            Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
        )
        val checked = BooleanArray(7) { i -> selectedDays.contains(dayCodes[i]) }

        val currentStyle = prefs.getString("phase1_style", "NIGHT_CLOCK")
        val styleTitle = if (currentStyle == "SUNRISE") "Фаза 1: Световой рассвет" else "Фаза 1: Ночные часы"

        AlertDialog.Builder(this)
            .setTitle("Настройки будильника")
            .setMultiChoiceItems(dayNames, checked) { _, which, isChecked ->
                if (isChecked) selectedDays.add(dayCodes[which])
                else selectedDays.remove(dayCodes[which])
            }
            .setNeutralButton(styleTitle) { _, _ ->
                val next = if (currentStyle == "SUNRISE") "NIGHT_CLOCK" else "SUNRISE"
                prefs.edit().putString("phase1_style", next).apply()
                info.text = if (next == "SUNRISE") "Фаза 1: Световой рассвет" else "Фаза 1: Ночные часы"
            }
            .setPositiveButton("Готово", null)
            .show()
    }
}
