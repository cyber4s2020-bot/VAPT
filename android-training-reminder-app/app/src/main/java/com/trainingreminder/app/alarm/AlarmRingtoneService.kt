package com.trainingreminder.app.alarm

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.CombinedVibration
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.trainingreminder.app.R
import com.trainingreminder.app.data.AppDatabase
import com.trainingreminder.app.data.ReminderStatus
import com.trainingreminder.app.notification.CHANNEL_ID_ALARM
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val ACTION_DISMISS = "com.trainingreminder.app.action.DISMISS"
const val ACTION_SNOOZE = "com.trainingreminder.app.action.SNOOZE"

private const val AUTO_STOP_MILLIS = 5 * 60 * 1000L
private const val SNOOZE_MINUTES = 5

/**
 * Foreground service that actually rings the alarm: plays the alarm tone on
 * a loop, vibrates, and posts the full-screen notification that surfaces
 * [AlarmRingActivity] over the lock screen, mirroring how the platform Clock
 * app behaves.
 */
class AlarmRingtoneService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService<VibratorManager>()?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService()
        }
    }

    private val scope = CoroutineScope(Dispatchers.Default)
    private var autoStopJob: Job? = null

    private var instanceId: String? = null
    private var alarmRequestCode: Int = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISMISS -> {
                stopAndFinish(ReminderStatus.DISMISSED)
                return START_NOT_STICKY
            }
            ACTION_SNOOZE -> {
                stopAndFinish(ReminderStatus.SNOOZED, reschedule = true)
                return START_NOT_STICKY
            }
        }

        val title = intent?.getStringExtra(EXTRA_EVENT_TITLE) ?: getString(R.string.app_name)
        val leadMinutes = intent?.getIntExtra(EXTRA_LEAD_MINUTES, 15) ?: 15
        instanceId = intent?.getStringExtra(EXTRA_INSTANCE_ID)
        alarmRequestCode = instanceId?.let { AlarmScheduler(this).requestCodeFor(it) } ?: -1

        startForeground(NOTIFICATION_ID, buildNotification(title, leadMinutes, intent))
        startRinging()

        autoStopJob = scope.launch {
            delay(AUTO_STOP_MILLIS)
            stopAndFinish(ReminderStatus.SNOOZED, reschedule = true)
        }

        return START_STICKY
    }

    private fun buildNotification(title: String, leadMinutes: Int, sourceIntent: Intent?): Notification {
        val fullScreenIntent = Intent(this, AlarmRingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            sourceIntent?.extras?.let { putExtras(it) }
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            alarmRequestCode,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val dismissPendingIntent = servicePendingIntent(ACTION_DISMISS)
        val snoozePendingIntent = servicePendingIntent(ACTION_SNOOZE)

        return NotificationCompat.Builder(this, CHANNEL_ID_ALARM)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.alarm_notification_title))
            .setContentText(getString(R.string.alarm_notification_text, title, leadMinutes))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(0, getString(R.string.snooze), snoozePendingIntent)
            .addAction(0, getString(R.string.dismiss), dismissPendingIntent)
            .build()
    }

    private fun servicePendingIntent(action: String): PendingIntent {
        val intent = Intent(this, AlarmRingtoneService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            alarmRequestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun startRinging() {
        val soundUri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            isLooping = true
            try {
                setDataSource(this@AlarmRingtoneService, soundUri)
                prepare()
                start()
            } catch (_: Exception) {
                // If the tone can't be loaded, fall back to vibration only.
            }
        }

        val pattern = longArrayOf(0, 800, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val effect = VibrationEffect.createWaveform(pattern, 0)
            vibrator?.vibrate(effect)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun stopAndFinish(status: ReminderStatus, reschedule: Boolean = false) {
        autoStopJob?.cancel()
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null
        vibrator?.cancel()

        val id = instanceId
        scope.launch {
            if (id != null) {
                val dao = AppDatabase.getInstance(applicationContext).scheduledReminderDao()
                dao.getByInstanceId(id)?.let { reminder ->
                    dao.update(reminder.copy(status = status))
                    if (reschedule) {
                        AlarmScheduler(applicationContext).schedule(
                            instanceId = reminder.instanceId,
                            requestCode = reminder.alarmRequestCode,
                            eventTitle = reminder.eventTitle,
                            eventLocation = null,
                            eventStartMillis = reminder.eventStartMillis,
                            triggerAtMillis = System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L,
                            leadMinutes = SNOOZE_MINUTES,
                        )
                    }
                }
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        autoStopJob?.cancel()
        mediaPlayer?.release()
        vibrator?.cancel()
    }

    companion object {
        const val NOTIFICATION_ID = 42
    }
}
