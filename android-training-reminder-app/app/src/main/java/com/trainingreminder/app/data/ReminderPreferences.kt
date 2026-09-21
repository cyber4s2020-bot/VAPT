package com.trainingreminder.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "training_reminder_prefs")

data class ReminderSettings(
    val leadMinutes: Int = DEFAULT_LEAD_MINUTES,
    val keywordFilter: String = "",
) {
    companion object {
        const val DEFAULT_LEAD_MINUTES = 15
    }
}

/**
 * User-configurable settings: how many minutes before an event the alarm
 * should fire (defaults to 15, as requested) and an optional keyword used to
 * only pick out "training" sessions/events rather than every calendar entry.
 */
class ReminderPreferences(private val context: Context) {

    private val leadMinutesKey = intPreferencesKey("lead_minutes")
    private val keywordFilterKey = stringPreferencesKey("keyword_filter")

    val settings: Flow<ReminderSettings> = context.dataStore.data.map { prefs ->
        ReminderSettings(
            leadMinutes = prefs[leadMinutesKey] ?: ReminderSettings.DEFAULT_LEAD_MINUTES,
            keywordFilter = prefs[keywordFilterKey] ?: "",
        )
    }

    suspend fun setLeadMinutes(minutes: Int) {
        context.dataStore.edit { it[leadMinutesKey] = minutes.coerceIn(1, 24 * 60) }
    }

    suspend fun setKeywordFilter(keyword: String) {
        context.dataStore.edit { it[keywordFilterKey] = keyword.trim() }
    }
}
