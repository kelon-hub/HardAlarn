package com.example.hardalarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class AlarmService : Service() {

    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var escalating = false
    private var phase = 1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        phase = intent?.getIntExtra(EXTRA_PHASE, 1) ?: 1
        startAsForeground()
        play()
        if (phase == 2) startEscalation()
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Будильник", NotificationManager.IMPORTANCE_HIGH)
        )
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Жёсткий будильник")
            .setContentText(if (phase == 1) "Фаза 1: вставай" else "Фаза 2: реши примеры")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun play() {
        stopPlaying()
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        player = MediaPlayer().apply {
            setDataSource(this@AlarmService, uri)
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
    }

    private fun startEscalation() {
        if (escalating) return
        escalating = true
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val step = object : Runnable {
            override fun run() {
                val cur = audio.getStreamVolume(AudioManager.STREAM_ALARM)
                if (cur < max) {
                    audio.setStreamVolume(AudioManager.STREAM_ALARM, cur + 1, 0)
                }
                if (cur + 1 < max) handler.postDelayed(this, STEP_MS)
            }
        }
        handler.postDelayed(step, STEP_MS)
    }

    private fun stopPlaying() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopPlaying()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "hard_alarm"
        const val EXTRA_PHASE = "phase"
        private const val NOTIF_ID = 2
        private const val STEP_MS = 30_000L

        fun start(context: Context, phase: Int) {
            context.startForegroundService(
                Intent(context, AlarmService::class.java).putExtra(EXTRA_PHASE, phase)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AlarmService::class.java))
        }
    }
}
