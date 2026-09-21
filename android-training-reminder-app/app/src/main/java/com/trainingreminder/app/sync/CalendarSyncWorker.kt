package com.trainingreminder.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.trainingreminder.app.alarm.AlarmScheduler
import com.trainingreminder.app.data.AppDatabase
import com.trainingreminder.app.data.CalendarRepository
import com.trainingreminder.app.data.ReminderPreferences
import com.trainingreminder.app.data.ReminderStatus
import com.trainingreminder.app.data.ScheduledReminder
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

private const val SCAN_WINDOW_DAYS = 30
private const val STALE_CUTOFF_MILLIS = 24 * 60 * 60 * 1000L

/**
 * Periodic backstop that scans the Calendar Provider for upcoming events and
 * makes sure every matching one has an [android.app.AlarmManager] alarm
 * scheduled [ReminderPreferences.leadMinutes] before it starts. Because the
 * alarm itself is scheduled with AlarmManager well ahead of time, this
 * worker only needs to run occasionally (WorkManager's minimum periodic
 * interval is 15 minutes) to pick up newly added/changed/removed events -
 * it is not what fires the alarm at the reminder time.
 */
class CalendarSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!CalendarRepository.hasCalendarPermission(applicationContext)) {
            return Result.success()
        }

        val prefs = ReminderPreferences(applicationContext).settings.first()
        val repository = CalendarRepository(applicationContext)
        val dao = AppDatabase.getInstance(applicationContext).scheduledReminderDao()
        val scheduler = AlarmScheduler(applicationContext)

        val now = System.currentTimeMillis()
        val leadMillis = prefs.leadMinutes * 60_000L

        val upcomingEvents = repository.queryUpcomingEvents(SCAN_WINDOW_DAYS, prefs.keywordFilter)
        val upcomingIds = upcomingEvents.map { it.instanceId }.toSet()

        val existingReminders = dao.getAll().associateBy { it.instanceId }

        // Schedule new / changed events.
        for (event in upcomingEvents) {
            if (event.startTimeMillis <= now) continue

            val desiredReminderTime = (event.startTimeMillis - leadMillis).coerceAtLeast(now)
            val existing = existingReminders[event.instanceId]

            val needsScheduling = when {
                existing == null -> true
                existing.status != ReminderStatus.SCHEDULED -> false
                existing.reminderTimeMillis != desiredReminderTime -> true
                else -> false
            }
            if (!needsScheduling) continue

            val requestCode = scheduler.requestCodeFor(event.instanceId)
            scheduler.schedule(
                instanceId = event.instanceId,
                requestCode = requestCode,
                eventTitle = event.title,
                eventLocation = event.location,
                eventStartMillis = event.startTimeMillis,
                triggerAtMillis = desiredReminderTime,
                leadMinutes = prefs.leadMinutes,
            )
            dao.upsert(
                ScheduledReminder(
                    instanceId = event.instanceId,
                    eventId = event.eventId,
                    eventTitle = event.title,
                    eventStartMillis = event.startTimeMillis,
                    reminderTimeMillis = desiredReminderTime,
                    alarmRequestCode = requestCode,
                    status = ReminderStatus.SCHEDULED,
                ),
            )
        }

        // Cancel reminders for events that were deleted or no longer match the filter,
        // but haven't started yet.
        for (reminder in existingReminders.values) {
            if (reminder.status != ReminderStatus.SCHEDULED) continue
            if (reminder.eventStartMillis <= now) continue
            if (reminder.instanceId in upcomingIds) continue

            scheduler.cancel(reminder.alarmRequestCode)
            dao.update(reminder.copy(status = ReminderStatus.CANCELLED))
        }

        dao.deleteOlderThan(now - STALE_CUTOFF_MILLIS)

        return Result.success()
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "calendar_sync_periodic"

        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<CalendarSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build(),
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun runNow(context: Context) {
            val request = androidx.work.OneTimeWorkRequestBuilder<CalendarSyncWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "calendar_sync_now",
                androidx.work.ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
