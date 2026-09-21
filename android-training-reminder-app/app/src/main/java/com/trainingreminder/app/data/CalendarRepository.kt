package com.trainingreminder.app.data

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import java.util.concurrent.TimeUnit

/**
 * Reads events from the device's Calendar Provider. This is the provider
 * that Google's own Calendar app and sync adapter populate once the user has
 * added a Google account on the device, so it transparently reflects Google
 * Calendar without needing a separate Google API integration or OAuth flow.
 */
class CalendarRepository(private val context: Context) {

    /**
     * Returns every event instance starting in [windowStart]..[windowEnd]
     * (epoch millis), optionally filtered to titles/descriptions containing
     * [keyword] (case-insensitive). Uses CalendarContract.Instances so that
     * recurring events are already expanded into their individual
     * occurrences.
     */
    fun queryUpcomingEvents(
        windowStart: Long,
        windowEnd: Long,
        keyword: String = "",
    ): List<CalendarEvent> {
        val events = mutableListOf<CalendarEvent>()

        val uriBuilder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(uriBuilder, windowStart)
        ContentUris.appendId(uriBuilder, windowEnd)
        val uri = uriBuilder.build()

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances._ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances.DESCRIPTION,
        )

        val selection: String?
        val selectionArgs: Array<String>?
        if (keyword.isNotBlank()) {
            selection =
                "(${CalendarContract.Instances.TITLE} LIKE ? OR ${CalendarContract.Instances.DESCRIPTION} LIKE ?)"
            val like = "%$keyword%"
            selectionArgs = arrayOf(like, like)
        } else {
            selection = null
            selectionArgs = null
        }

        context.contentResolver.query(
            uri,
            projection,
            selection,
            selectionArgs,
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { cursor ->
            val eventIdCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
            val calendarIdCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_ID)
            val instanceIdCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances._ID)
            val titleCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            val locationCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)
            val beginCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            val endCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
            val allDayCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
            val calNameCol =
                cursor.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val allDay = cursor.getInt(allDayCol) != 0
                // All-day events don't have a meaningful "15 minutes before" moment.
                if (allDay) continue

                events += CalendarEvent(
                    eventId = cursor.getLong(eventIdCol),
                    calendarId = cursor.getLong(calendarIdCol),
                    instanceId = "${cursor.getLong(eventIdCol)}_${cursor.getLong(beginCol)}",
                    title = cursor.getString(titleCol) ?: "(No title)",
                    location = cursor.getString(locationCol),
                    startTimeMillis = cursor.getLong(beginCol),
                    endTimeMillis = cursor.getLong(endCol),
                    allDay = false,
                    calendarDisplayName = cursor.getString(calNameCol),
                )
            }
        }

        return events
    }

    /** Convenience for scanning the next [days] days from now. */
    fun queryUpcomingEvents(days: Int, keyword: String = ""): List<CalendarEvent> {
        val now = System.currentTimeMillis()
        val end = now + TimeUnit.DAYS.toMillis(days.toLong())
        return queryUpcomingEvents(now, end, keyword)
    }

    /**
     * Emits whenever the Calendar Provider content changes (event added,
     * edited, deleted, or a fresh Google Calendar sync lands), so the app can
     * re-scan for new sessions without waiting for the next periodic sync.
     */
    fun observeChanges(): Flow<Unit> = callbackFlow {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        context.contentResolver.registerContentObserver(
            CalendarContract.Instances.CONTENT_URI,
            true,
            observer,
        )
        // Emit once immediately so collectors get an initial value.
        trySend(Unit)
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }.conflate()

    companion object {
        val REQUIRED_PERMISSION: String = android.Manifest.permission.READ_CALENDAR

        fun hasCalendarPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(
                context,
                REQUIRED_PERMISSION,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}
