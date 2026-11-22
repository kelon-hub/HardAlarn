package com.example.hardalarm
import android.animation.ValueAnimator
import android.graphics.Color
import android.widget.ImageView

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

    private var player: MediaPlayer? = null
    private var toneGen: ToneGenerator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isMuted = false

    private lateinit var title: TextView
    private lateinit var question: TextView
    private lateinit var progress: TextView
    private lateinit var buttons: List<Button>
    private lateinit var btnMute: Button
    private lateinit var stopButton: Button

    private val unmuteRunnable = Runnable {
        isMuted = false
        btnMute.isEnabled = true
        btnMute.text = "Приглушить звук на 20 сек"
        startSound()
    }

    private val volumeStepRunnable = object : Runnable {
        override fun run() {
            if (!isMuted && phase == 2) {
                val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                val cur = audio.getStreamVolume(AudioManager.STREAM_ALARM)
                if (cur < max) {
                    runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, cur + 1, 0) }
                }
            }
            handler.postDelayed(this, 30_000L)
        }
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

        title = findViewById(R.id.phaseTitle)
        question = findViewById(R.id.question)
        progress = findViewById(R.id.progress)
        btnMute = findViewById(R.id.btnMute)
        stopButton = findViewById(R.id.stopButton)
        buttons = listOf(R.id.opt0, R.id.opt1, R.id.opt2, R.id.opt3).map { findViewById(it) }

        btnMute.setOnClickListener {
            isMuted = true
            btnMute.isEnabled = false
            btnMute.text = "Звук на паузе (20 сек)..."
            stopSound()
            handler.removeCallbacks(unmuteRunnable)
            handler.postDelayed(unmuteRunnable, 20_000L)
        }

        stopButton.setOnClickListener {
            if (phase == 1) {
                stopSound()
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

    private fun startSound() {
        if (isMuted) return
        stopSound()

        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxAlarm = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        if (phase == 1) {
            val targetAlarm = (maxAlarm * 0.8).toInt().coerceAtLeast(1)
            if (audio.getStreamVolume(AudioManager.STREAM_ALARM) < targetAlarm) {
                runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, targetAlarm, 0) }
            }
        } else {
            runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, 1, 0) }
        }

        val prefs = getSharedPreferences("hard_alarm", Context.MODE_PRIVATE)
        val savedUriStr = prefs.getString("music_uri", null)
        val customUri = savedUriStr?.let { runCatching { Uri.parse(it) }.getOrNull() }
        val fallbackUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        val started = (customUri != null && tryPlayUri(customUri)) ||
            (fallbackUri != null && tryPlayUri(fallbackUri))

        if (!started) {
            runCatching {
                toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                toneGen?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 60_000)
            }
        }
    }

    private fun tryPlayUri(uri: Uri): Boolean {
        return runCatching {
            player = MediaPlayer().apply {
                setDataSource(this@AlarmActivity, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
            player?.isPlaying == true
        }.getOrDefault(false)
    }

    private fun stopSound() {
        player?.let { runCatching { it.stop() }; runCatching { it.release() } }
        player = null
        toneGen?.let { runCatching { it.stopTone() }; runCatching { it.release() } }
        toneGen = null
    }

    private fun finishPhase2AndShowGoals() {
        handler.removeCallbacksAndMessages(null)
        stopSound()
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
        isMuted = false
        emergencyTaps = 0
        stopButton.text = "СТОП"
        handler.removeCallbacksAndMessages(null)

        AlarmService.stop(this)
        startSound()

        if (phase == 1) {
            title.text = "Фаза 1 — проснись!"
            question.visibility = View.GONE
            btnMute.visibility = View.GONE
            progress.text = "Просто нажми «Стоп».\nНо через 3 минуты придёт фаза 2 — сама."
            buttons.forEach { it.visibility = View.GONE }
        } else {
            title.text = "Фаза 2 — реши 3 примера"
            btnMute.visibility = View.VISIBLE
            handler.postDelayed(volumeStepRunnable, 30_000L)
            problems = MathGenerator.generate(3)
            index = 0
            showProblem()
        }
    }

    private fun showProblem() {
        if (index >= problems.size) {
            solved = true
            handler.removeCallbacksAndMessages(null)
            stopSound()
            progress.text = "Все 3 примера решены. Свобода!"
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
                        problems = MathGenerator.generate(3)
                        index = 0
                        showProblem()
                    }
                }
            }
        }
    }

        private var sunriseAnimator: ValueAnimator? = null
    private var brightnessAnimator: ValueAnimator? = null
    
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
