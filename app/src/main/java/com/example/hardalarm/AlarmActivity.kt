package com.example.hardalarm

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

class AlarmActivity : AppCompatActivity() {

    private var phase = 1
    private var problems: List<MathGenerator.Problem> = emptyList()
    private var index = 0
    private var solved = false
    private var emergencyTaps = 0

    private val handler = Handler(Looper.getMainLooper())
    private var sunriseAnimator: ValueAnimator? = null
    private var brightnessAnimator: ValueAnimator? = null

    private lateinit var rootLayout: View
    private lateinit var title: TextView
    private lateinit var question: TextView
    private lateinit var progress: TextView
    private lateinit var buttons: List<Button>
    private lateinit var btnMute: Button
    private lateinit var stopButton: Button

    private var notePhoto: ImageView? = null
    private var noteTextView: TextView? = null

    private val unmuteUiRunnable = Runnable {
        btnMute.isEnabled = true
        btnMute.text = "Приглушить звук на 20 сек"
    }

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

        rootLayout = findViewById(android.R.id.content)
        title = findViewById(R.id.phaseTitle)
        question = findViewById(R.id.question)
        progress = findViewById(R.id.progress)
        btnMute = findViewById(R.id.btnMute)
        stopButton = findViewById(R.id.stopButton)
        buttons = listOf(R.id.opt0, R.id.opt1, R.id.opt2, R.id.opt3).map { findViewById(it) }

        val photoId = resources.getIdentifier("notePhoto", "id", packageName)
        if (photoId != 0) notePhoto = findViewById(photoId)
        val noteId = resources.getIdentifier("noteText", "id", packageName)
        if (noteId != 0) noteTextView = findViewById(noteId)

        btnMute.setOnClickListener {
            btnMute.isEnabled = false
            btnMute.text = "Звук на паузе (20 сек)..."
            AlarmService.muteTemporarily(this)
            handler.removeCallbacks(unmuteUiRunnable)
            handler.postDelayed(unmuteUiRunnable, 20_000L)
        }

        stopButton.setOnClickListener {
            if (phase == 1) {
                AlarmService.stop(this)
                finish()
            } else if (solved) {
                finishPhase2AndShowGoals()
            } else {
                emergencyTaps++
                if (emergencyTaps >= 15) {
                    finishPhase2AndShowGoals()
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

    private fun finishPhase2AndShowGoals() {
        handler.removeCallbacksAndMessages(null)
        val prefs = getSharedPreferences("hard_alarm", MODE_PRIVATE)
        val days = prefs.getStringSet("days", emptySet())?.map { it.toInt() }?.toSet() ?: emptySet()
        if (days.isNotEmpty()) {
            AlarmScheduler.schedule(this, prefs.getInt("hour", 5), prefs.getInt("minute", 0), days)
        } else {
            prefs.edit().putBoolean("enabled", false).apply()
        }
        AlarmService.stop(this)
        startActivity(Intent(this, GoalsActivity::class.java))
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
        handler.removeCallbacksAndMessages(null)
        stopVisualAnimators()

        AlarmService.start(this, phase)
        loadNoteMedia()

        if (phase == 1) {
            applyPhase1Style()
            title.text = "Фаза 1 — проснись!"
            question.visibility = View.GONE
            btnMute.visibility = View.GONE
            progress.text = "Просто нажми «Стоп».\nНо через 2 минуты придёт фаза 2 — сама."
            buttons.forEach { it.visibility = View.GONE }
        } else {
            rootLayout.setBackgroundColor(Color.parseColor("#131313"))
            title.setTextColor(Color.WHITE)
            progress.setTextColor(Color.LTGRAY)

            title.text = "Фаза 2 — реши 5 примеров"
            btnMute.visibility = View.VISIBLE
            problems = MathGenerator.generate(5)
            index = 0
            showProblem()
        }
    }

    private fun applyPhase1Style() {
        val prefs = getSharedPreferences("hard_alarm", MODE_PRIVATE)
        val styleMode = prefs.getString("phase1_style", "NIGHT_CLOCK")

        if (styleMode == "SUNRISE") {
            val colors = intArrayOf(
                Color.parseColor("#FF0033"),
                Color.parseColor("#FFBF00"),
                Color.parseColor("#FFFFE0"),
                Color.parseColor("#FFFFFF")
            )
            sunriseAnimator = ValueAnimator.ofArgb(*colors).apply {
                duration = 60_000L
                addUpdateListener { anim ->
                    val currentBg = anim.animatedValue as Int
                    rootLayout.setBackgroundColor(currentBg)
                    val textColor = if (anim.animatedFraction > 0.5f) Color.parseColor("#1C1C1C") else Color.WHITE
                    title.setTextColor(textColor)
                    progress.setTextColor(textColor)
                }
                start()
            }

            val lp = window.attributes
            brightnessAnimator = ValueAnimator.ofFloat(0.1f, 1.0f).apply {
                duration = 60_000L
                addUpdateListener { anim ->
                    lp.screenBrightness = anim.animatedValue as Float
                    window.attributes = lp
                }
                start()
            }
        } else {
            rootLayout.setBackgroundColor(Color.parseColor("#000000"))
            val neonBlue = Color.parseColor("#00BFFF")
            title.setTextColor(neonBlue)
            progress.setTextColor(neonBlue)
        }
    }

    private fun loadNoteMedia() {
        val prefs = getSharedPreferences("hard_alarm", MODE_PRIVATE)
        val photoUriStr = prefs.getString("note_photo_uri", null)
        val noteStr = prefs.getString("note_text", null)

        noteTextView?.apply {
            if (!noteStr.isNullOrBlank()) {
                text = noteStr
                visibility = View.VISIBLE
            } else {
                visibility = View.GONE
            }
        }

        notePhoto?.apply {
            if (!photoUriStr.isNullOrBlank()) {
                val loaded = runCatching {
                    setImageURI(Uri.parse(photoUriStr))
                    true
                }.getOrDefault(false)
                visibility = if (loaded) View.VISIBLE else View.GONE
            } else {
                visibility = View.GONE
            }
        }
    }

    private fun showProblem() {
        if (index >= problems.size) {
            solved = true
            handler.removeCallbacksAndMessages(null)
            AlarmService.stop(this)
            progress.text = "Все 5 примеров решены. Свобода!"
            question.visibility = View.GONE
            btnMute.visibility = View.GONE
            buttons.forEach { it.visibility = View.GONE }
            stopButton.text = "СТОП И ПОКАЗАТЬ ЦЕЛИ"
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

    private fun stopVisualAnimators() {
        sunriseAnimator?.cancel()
        brightnessAnimator?.cancel()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopVisualAnimators()
        AlarmService.stop(this)
        super.onDestroy()
    }
}
