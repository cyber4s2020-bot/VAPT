package com.trainingreminder.app.data

/**
 * A single occurrence of a calendar event, read from the on-device Calendar
 * Provider (CalendarContract), which the Google Calendar app keeps in sync
 * with the user's Google account.
 *
 * [instanceId] identifies one occurrence of a (possibly recurring) event and
 * is stable across syncs, so it doubles as the primary key for scheduled
 * reminders.
 */
data class CalendarEvent(
    val eventId: Long,
    val calendarId: Long,
    val instanceId: String,
    val title: String,
    val location: String?,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val allDay: Boolean,
    val calendarDisplayName: String?,
)
