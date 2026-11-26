package com.example.hardalarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import kotlin.math.roundToInt

class AlarmService : Service() {

    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var escalating = false
    private var phase = 1
    private var alarmId = -1
    private var destroyed = false
    private var currentVolume = 0.8f

    private val resumeRunnable = Runnable {
        if (!destroyed) play()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_MUTE) {
            handler.removeCallbacks(resumeRunnable)
            stopPlaying()
            handler.postDelayed(resumeRunnable, MUTE_MS)
            return START_NOT_STICKY
        }

        val newPhase = intent?.getIntExtra(EXTRA_PHASE, 1) ?: 1
        val newAlarmId = intent?.getIntExtra(EXTRA_ALARM_ID, -1) ?: -1

        // Если этот же будильник и эта же фаза уже играют (запущены из AlarmReceiver) — не перезапускаем трек с начала
        if (player?.isPlaying == true && newPhase == phase && newAlarmId == alarmId) {
            return START_NOT_STICKY
        }

        if (newPhase != phase) {
            escalating = false
            handler.removeCallbacksAndMessages(null)
        }
        phase = newPhase
        alarmId = newAlarmId
        currentVolume = if (phase == 1) 0.8f else 0.3f

        handler.removeCallbacks(resumeRunnable)
        startAsForeground()
        setSystemAlarmMaxVolume()
        play()

        if (phase == 2) startEscalation()
        return START_NOT_STICKY
    }

    private fun setSystemAlarmMaxVolume() {
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        runCatching {
            audio.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
        }
    }

    private fun startAsForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Будильник", NotificationManager.IMPORTANCE_HIGH)
        )

        val openIntent = Intent(this, AlarmActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("phase", phase)
            .putExtra("alarm_id", alarmId)

        val fsPending = PendingIntent.getActivity(
            this, 999, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("The Death Clocks")
            .setContentText(if (phase == 1) "Фаза 1: вставай" else "Фаза 2: реши 5 примеров")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fsPending, true)
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun play() {
        if (destroyed) return
        stopPlaying()

        // Берём индивидуальную мелодию именно этого будильника (или общую/стандартную)
        val item = if (alarmId > 0) AlarmStorage.getById(this, alarmId) else null
        val customUri = item?.musicUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
        val fallbackUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val started = (customUri != null && tryPlayUri(customUri)) ||
            (fallbackUri != null && tryPlayUri(fallbackUri))

        if (!started && fallbackUri != null) {
            tryPlayUri(fallbackUri)
        }
    }

    private fun tryPlayUri(uri: Uri): Boolean {
        return runCatching {
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
                setVolume(currentVolume, currentVolume)
                start()
            }
            player?.isPlaying == true
        }.getOrDefault(false)
    }

    private fun startEscalation() {
        if (escalating) return
        escalating = true
        val step = object : Runnable {
            override fun run() {
                if (destroyed || phase != 2) return
                if (currentVolume < 1.0f) {
                    currentVolume = (((currentVolume + 0.1f) * 10).roundToInt() / 10f)
                        .coerceAtMost(1.0f)
                    runCatching {
                        player?.setVolume(currentVolume, currentVolume)
                    }
                    if (currentVolume < 1.0f) {
                        handler.postDelayed(this, STEP_MS)
                    }
                }
            }
        }
        handler.postDelayed(step, STEP_MS)
    }

    private fun stopPlaying() {
        player?.let { runCatching { it.stop() }; runCatching { it.release() } }
        player = null
    }

    override fun onDestroy() {
        destroyed = true
        handler.removeCallbacksAndMessages(null)
        stopPlaying()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "hard_alarm"
        const val EXTRA_PHASE = "phase"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val ACTION_MUTE = "com.example.hardalarm.MUTE"
        private const val NOTIF_ID = 2
        private const val STEP_MS = 30_000L
        private const val MUTE_MS = 20_000L

        fun start(context: Context, phase: Int, alarmId: Int = -1) {
            runCatching {
                context.startForegroundService(
                    Intent(context, AlarmService::class.java)
                        .putExtra(EXTRA_PHASE, phase)
                        .putExtra(EXTRA_ALARM_ID, alarmId)
                )
            }
        }

        fun muteTemporarily(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, AlarmService::class.java).setAction(ACTION_MUTE)
                )
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, AlarmService::class.java))
            }
        }
    }
}
