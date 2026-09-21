package com.trainingreminder.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import com.trainingreminder.app.MainActivity

const val EXTRA_INSTANCE_ID = "extra_instance_id"
const val EXTRA_EVENT_TITLE = "extra_event_title"
const val EXTRA_EVENT_LOCATION = "extra_event_location"
const val EXTRA_EVENT_START_MILLIS = "extra_event_start_millis"
const val EXTRA_LEAD_MINUTES = "extra_lead_minutes"

/**
 * Wraps [AlarmManager.setAlarmClock], the same API the stock Clock app uses.
 * Unlike [AlarmManager.setExactAndAllowWhileIdle], an "alarm clock" alarm:
 *  - does not require the user to separately grant SCHEDULE_EXACT_ALARM,
 *  - is exempt from Doze/App Standby so it always fires on time, and
 *  - shows the alarm-clock icon in the status bar, signalling to the user
 *    that a wake-up style alert is pending, exactly like a real alarm.
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager: AlarmManager? = context.getSystemService()

    fun schedule(
        instanceId: String,
        requestCode: Int,
        eventTitle: String,
        eventLocation: String?,
        eventStartMillis: Long,
        triggerAtMillis: Long,
        leadMinutes: Int,
    ) {
        val manager = alarmManager ?: return

        val alarmIntent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_INSTANCE_ID, instanceId)
            putExtra(EXTRA_EVENT_TITLE, eventTitle)
            putExtra(EXTRA_EVENT_LOCATION, eventLocation)
            putExtra(EXTRA_EVENT_START_MILLIS, eventStartMillis)
            putExtra(EXTRA_LEAD_MINUTES, leadMinutes)
        }
        val operation = PendingIntent.getBroadcast(
            context,
            requestCode,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val showIntent = PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        manager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent),
            operation,
        )
    }

    fun cancel(requestCode: Int) {
        val manager = alarmManager ?: return
        val alarmIntent = Intent(context, AlarmReceiver::class.java)
        val operation = PendingIntent.getBroadcast(
            context,
            requestCode,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.cancel(operation)
        operation.cancel()
    }

    /** Stable-ish request code derived from the event instance id. */
    fun requestCodeFor(instanceId: String): Int = instanceId.hashCode()
}
