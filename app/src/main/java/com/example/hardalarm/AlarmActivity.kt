package com.example.hardalarm

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

class AlarmActivity : AppCompatActivity() {

    private var phase = 1
    private var problems: List<MathGenerator.Problem> = emptyList()
    private var index = 0
    private var solved = false
    private var emergencyTaps = 0

    private lateinit var title: TextView
    private lateinit var question: TextView
    private lateinit var progress: TextView
    private lateinit var buttons: List<Button>
    private lateinit var btnMute: Button
    private lateinit var stopButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_alarm)

        title = findViewById(R.id.phaseTitle)
        question = findViewById(R.id.question)
        progress = findViewById(R.id.progress)
        btnMute = findViewById(R.id.btnMute)
        stopButton = findViewById(R.id.stopButton)
        buttons = listOf(R.id.opt0, R.id.opt1, R.id.opt2, R.id.opt3).map { findViewById(it) }

        btnMute.setOnClickListener {
            btnMute.isEnabled = false
            btnMute.text = "Звук на паузе (20 сек)..."
            AlarmService.muteTemporarily(this)
        }

        stopButton.setOnClickListener {
            if (phase == 1) {
                AlarmService.stop(this)
                finish()
            } else if (solved) {
                finishAlarm()
            } else {
                emergencyTaps++
                if (emergencyTaps >= 15) {
                    finishAlarm()
                } else {
                    stopButton.text = "Реши примеры (сброс: $emergencyTaps/15)"
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { }
        })

        setup()
    }

    private fun finishAlarm() {
        val prefs = getSharedPreferences("hard_alarm", MODE_PRIVATE)
        val days = prefs.getStringSet("days", emptySet())?.map { it.toInt() }?.toSet() ?: emptySet()
        AlarmScheduler.schedule(this, prefs.getInt("hour", 5), prefs.getInt("minute", 0), days)
        AlarmService.stop(this)
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        setup()
    }

    private fun setup() {
        phase = intent.getIntExtra("phase", 1)
        solved = false
        emergencyTaps = 0
        stopButton.text = "СТОП"
AlarmService.start(this, phase)
        if (phase == 1) {
            title.text = "Фаза 1 — проснись!"
            question.visibility = View.GONE
            btnMute.visibility = View.GONE
            progress.text = "Просто нажми «Стоп».\nНо через 3 минуты придёт фаза 2 — сама."
            buttons.forEach { it.visibility = View.GONE }
        } else {
            title.text = "Фаза 2 — реши 5 примеров"
            btnMute.visibility = View.VISIBLE
            problems = MathGenerator.generate(5)
            index = 0
            showProblem()
        }
    }

    private fun showProblem() {
        if (index >= problems.size) {
            solved = true
            progress.text = "Все примеры решены. Свобода!"
            question.visibility = View.GONE
            btnMute.visibility = View.GONE
            buttons.forEach { it.visibility = View.GONE }
            stopButton.text = "СТОП"
            return
        }
        val p = problems[index]
        btnMute.isEnabled = true
        btnMute.text = "Приглушить звук на 20 сек"
        progress.text = "Пример ${index + 1} из ${problems.size}"
        question.visibility = View.VISIBLE
        question.text = p.question
        p.options.forEachIndexed { i, opt ->
            buttons[i].apply {
                visibility = View.VISIBLE
                text = opt
                setOnClickListener {
                    if (i == p.correctIndex) {
                        index++
                        showProblem()
                    } else {
                        progress.text = "Ошибка! Решаем заново"
                        problems = MathGenerator.generate(5)
                        index = 0
                        showProblem()
                    }
                }
            }
        }
    }
}
