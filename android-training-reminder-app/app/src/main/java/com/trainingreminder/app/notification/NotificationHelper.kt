package com.trainingreminder.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.trainingreminder.app.R

const val CHANNEL_ID_ALARM = "training_alarms"
const val CHANNEL_ID_SYNC = "calendar_sync"

object NotificationHelper {

    fun ensureChannels(context: Context) {
        val manager = NotificationManagerCompat.from(context)

        val alarmChannel = NotificationChannel(
            CHANNEL_ID_ALARM,
            context.getString(R.string.notif_channel_alarm_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notif_channel_alarm_desc)
            setBypassDnd(true)
            enableVibration(true)
            // Alarm sound is played manually via Ringtone in AlarmRingtoneService so it can be
            // stopped/looped precisely; keep the channel itself silent to avoid a double-beep.
            setSound(null, null)
        }

        val syncChannel = NotificationChannel(
            CHANNEL_ID_SYNC,
            context.getString(R.string.notif_channel_sync_name),
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = context.getString(R.string.notif_channel_sync_desc)
        }

        manager.createNotificationChannel(alarmChannel)
        manager.createNotificationChannel(syncChannel)
    }
}
