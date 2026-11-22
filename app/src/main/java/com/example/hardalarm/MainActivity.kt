package com.example.hardalarm

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.TimePicker
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var info: TextView
    private val dayBoxes = mutableMapOf<Int, CheckBox>()

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

    // Выбор фото из галереи для заметки будильника
    private val pickPhotoLauncher = registerForActivityResult(
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
                .putString("note_photo_uri", uri.toString())
                .apply()
            info.text = "Фото для заметки прикреплено!"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences("hard_alarm", Context.MODE_PRIVATE)
        val picker = findViewById<TimePicker>(R.id.timePicker)
        picker.setIs24HourView(true)
        picker.hour = prefs.getInt("hour", 5)
        picker.minute = prefs.getInt("minute", 0)
        info = findViewById(R.id.info)

        val savedDays = prefs.getStringSet("days", emptySet()) ?: emptySet()
        val boxIds = mapOf(
            R.id.cbMon to Calendar.MONDAY, R.id.cbTue to Calendar.TUESDAY,
            R.id.cbWed to Calendar.WEDNESDAY, R.id.cbThu to Calendar.THURSDAY,
            R.id.cbFri to Calendar.FRIDAY, R.id.cbSat to Calendar.SATURDAY,
            R.id.cbSun to Calendar.SUNDAY
        )
        boxIds.forEach { (id, day) ->
            findViewById<CheckBox>(id)?.apply {
                dayBoxes[day] = this
                isChecked = savedDays.contains(day.toString())
            }
        }

        findViewById<Button>(R.id.btnPickMusic).setOnClickListener {
            pickAudioLauncher.launch(arrayOf("audio/*"))
        }

        // Кнопка прикрепления фото (id: btnPickPhoto)
        val pickPhotoId = resources.getIdentifier("btnPickPhoto", "id", packageName)
        if (pickPhotoId != 0) {
            findViewById<Button>(pickPhotoId)?.setOnClickListener {
                pickPhotoLauncher.launch(arrayOf("image/*"))
            }
        }

        // Кнопка переключения стиля 1-й фазы: Ночные часы / Световой рассвет (id: btnPhase1Style)
        val styleBtnId = resources.getIdentifier("btnPhase1Style", "id", packageName)
        if (styleBtnId != 0) {
            findViewById<Button>(styleBtnId)?.apply {
                val currentStyle = prefs.getString("phase1_style", "NIGHT_CLOCK")
                text = if (currentStyle == "SUNRISE") "Фаза 1: Световой рассвет" else "Фаза 1: Ночные часы"

                setOnClickListener {
                    val nowSunrise = prefs.getString("phase1_style", "NIGHT_CLOCK") == "SUNRISE"
                    val nextStyle = if (nowSunrise) "NIGHT_CLOCK" else "SUNRISE"
                    prefs.edit().putString("phase1_style", nextStyle).apply()
                    text = if (nextStyle == "SUNRISE") "Фаза 1: Световой рассвет" else "Фаза 1: Ночные часы"
                    info.text = "Режим 1-й фазы обновлён!"
                }
            }
        }

        findViewById<Button>(R.id.btnGoals).setOnClickListener {
            startActivity(Intent(this, GoalsActivity::class.java))
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
            val days = dayBoxes.filter { it.value.isChecked }.map { it.key }.toSet()
            prefs.edit()
                .putBoolean("enabled", true)
                .putInt("hour", picker.hour)
                .putInt("minute", picker.minute)
                .putStringSet("days", days.map { it.toString() }.toSet())
                .apply()
            val ok = AlarmScheduler.schedule(this, picker.hour, picker.minute, days)
            info.text = if (ok)
                "Будильник на %02d:%02d. Фаза 2 — через 3 мин.".format(picker.hour, picker.minute)
            else
                "Не удалось поставить будильник — проверь права"
        }

        findViewById<Button>(R.id.btnCancel).setOnClickListener {
            prefs.edit().putBoolean("enabled", false).apply()
            AlarmScheduler.cancel(this)
            AlarmService.stop(this)
            info.text = "Будильник и повтор полностью сняты"
        }
    }
}
