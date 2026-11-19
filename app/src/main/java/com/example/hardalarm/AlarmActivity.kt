package com.example.hardalarm

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
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
            if (!isMuted) {
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
            AlarmService.muteTemporarily(this)
            handler.removeCallbacks(unmuteRunnable)
            handler.postDelayed(unmuteRunnable, 20_000L)
        }

        stopButton.setOnClickListener {
            if (phase == 1) {
                stopSound()
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

    private fun startSound() {
        if (isMuted) return
        stopSound()

        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxAlarm = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val targetAlarm = (maxAlarm * 0.8).toInt().coerceAtLeast(1)
        if (audio.getStreamVolume(AudioManager.STREAM_ALARM) < targetAlarm) {
            runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, targetAlarm, 0) }
        }

        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val started = runCatching {
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
            true
        }.getOrDefault(false)

        if (!started || player?.isPlaying != true) {
            runCatching {
                toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                toneGen?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 60_000)
            }
        }
    }

    private fun stopSound() {
        player?.let { runCatching { it.stop() }; runCatching { it.release() } }
        player = null
        toneGen?.let { runCatching { it.stopTone() }; runCatching { it.release() } }
        toneGen = null
    }

    private fun finishAlarm() {
        handler.removeCallbacksAndMessages(null)
        stopSound()
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
            title.text = "Фаза 2 — реши 5 примеров"
            btnMute.visibility = View.VISIBLE
            handler.postDelayed(volumeStepRunnable, 30_000L)
            problems = MathGenerator.generate(5)
            index = 0
            showProblem()
        }
    }

    private fun showProblem() {
        if (index >= problems.size) {
            solved = true
            handler.removeCallbacksAndMessages(null)
            stopSound()
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

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopSound()
        super.onDestroy()
    }
}
