package com.trainingreminder.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.trainingreminder.app.data.AppDatabase
import com.trainingreminder.app.data.ReminderStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fired by [android.app.AlarmManager] at T-minus-[EXTRA_LEAD_MINUTES]. Hands off to
 * [AlarmRingtoneService], which does the actual ringing/vibrating and shows the
 * full-screen alarm UI, since a BroadcastReceiver has only a few seconds to run.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val instanceId = intent.getStringExtra(EXTRA_INSTANCE_ID) ?: return

        val serviceIntent = Intent(context, AlarmRingtoneService::class.java).apply {
            putExtras(intent)
        }
        ContextCompat.startForegroundService(context, serviceIntent)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.getInstance(context).scheduledReminderDao()
                dao.getByInstanceId(instanceId)?.let { reminder ->
                    dao.update(reminder.copy(status = ReminderStatus.FIRED))
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
