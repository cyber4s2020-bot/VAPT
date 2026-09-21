package com.trainingreminder.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ReminderStatus {
    SCHEDULED,
    FIRED,
    DISMISSED,
    SNOOZED,
    CANCELLED,
}

/**
 * Local record of an alarm we've scheduled (or already fired) for one
 * calendar event instance. Lets the sync worker avoid double-scheduling the
 * same event and lets it cancel an alarm if the event was moved or deleted
 * from Google Calendar.
 */
@Entity(tableName = "scheduled_reminders")
data class ScheduledReminder(
    /** Matches [CalendarEvent.instanceId] ("eventId_startTimeMillis"). */
    @PrimaryKey val instanceId: String,
    val eventId: Long,
    val eventTitle: String,
    val eventStartMillis: Long,
    val reminderTimeMillis: Long,
    val alarmRequestCode: Int,
    val status: ReminderStatus,
)
