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

    private val pickAudioLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            getSharedPreferences("hard_alarm", Context.MODE_PRIVATE)
                .edit()
                .putString("music_uri", uri.toString())
                .apply()
            info.text = "Своя мелодия выбрана!"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences("hard_alarm", Context.MODE_PRIVATE)
        val picker = findViewById<TimePicker>(R.id.timePicker)
        picker.setIs24HourView(true)
        picker.hour = prefs.getInt("hour", 8)
        picker.minute = prefs.getInt("minute", 0)
        info = findViewById(R.id.info)

        val savedDays = prefs.getStringSet("days", emptySet()) ?: emptySet()
        savedDays.mapNotNull { it.toIntOrNull() }.forEach { selectedDays.add(it) }

        findViewById<View>(R.id.rowMessage).setOnClickListener {
            startActivity(Intent(this, GoalsActivity::class.java))
        }

        findViewById<View>(R.id.rowMelody).setOnClickListener {
            pickAudioLauncher.launch(arrayOf("audio/*"))
        }

        findViewById<Button>(R.id.btnAllAlarms).setOnClickListener {
            val enabled = prefs.getBoolean("enabled", false)
            val msg = if (enabled) {
                "Активен: %02d:%02d (Фаза 2 через 2 мин.)".format(
                    prefs.getInt("hour", 8),
                    prefs.getInt("minute", 0)
                )
            } else {
                "Активных будильников пока нет"
            }
            AlertDialog.Builder(this)
                .setTitle("Все будильники")
                .setMessage(msg)
                .setPositiveButton("ОК", null)
                .setNegativeButton("Снять все") { _, _ ->
                    prefs.edit().putBoolean("enabled", false).apply()
                    AlarmScheduler.cancel(this)
                    AlarmService.stop(this)
                    info.text = "Будильник снят"
                }
                .show()
        }

        findViewById<ImageButton>(R.id.btnAlarmSettings).setOnClickListener {
            showAlarmSettingsDialog()
        }

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

            prefs.edit()
                .putBoolean("enabled", true)
                .putInt("hour", picker.hour)
                .putInt("minute", picker.minute)
                .putStringSet("days", selectedDays.map { it.toString() }.toSet())
                .apply()

            val ok = AlarmScheduler.schedule(this, picker.hour, picker.minute, selectedDays)
            info.text = if (ok)
                "Сохранено на %02d:%02d. Фаза 2 — через 2 мин.".format(picker.hour, picker.minute)
            else
                "Не удалось поставить будильник — проверь права"
        }

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
        val styleTitle = if (currentStyle == "SUNRISE") "Режим 1 фазы: Световой рассвет" else "Режим 1 фазы: Ночные часы"

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
