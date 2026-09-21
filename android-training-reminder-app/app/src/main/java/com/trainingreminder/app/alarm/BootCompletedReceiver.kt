package com.trainingreminder.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.trainingreminder.app.sync.CalendarSyncWorker

/**
 * AlarmManager alarms do not survive a reboot, so on BOOT_COMPLETED (and
 * after the app is updated) we re-run a full calendar sync, which
 * re-schedules an alarm for every still-upcoming, not-yet-fired reminder.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val request = OneTimeWorkRequestBuilder<CalendarSyncWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "boot_resync",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
