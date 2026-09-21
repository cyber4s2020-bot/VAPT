package com.trainingreminder.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trainingreminder.app.data.AppDatabase
import com.trainingreminder.app.data.CalendarEvent
import com.trainingreminder.app.data.CalendarRepository
import com.trainingreminder.app.data.ReminderPreferences
import com.trainingreminder.app.data.ReminderSettings
import com.trainingreminder.app.data.ReminderStatus
import com.trainingreminder.app.sync.CalendarSyncWorker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val SCAN_WINDOW_DAYS = 30

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CalendarRepository(application)
    private val preferences = ReminderPreferences(application)
    private val dao = AppDatabase.getInstance(application).scheduledReminderDao()

    val settings: StateFlow<ReminderSettings> = preferences.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ReminderSettings(),
    )

    val hasCalendarPermission = { CalendarRepository.hasCalendarPermission(application) }

    private val calendarChanges = repository.observeChanges()

    val upcomingEvents: StateFlow<List<CalendarEvent>> = combine(
        preferences.settings,
        calendarChanges,
    ) { settings, _ ->
        if (!hasCalendarPermission()) emptyList()
        else repository.queryUpcomingEvents(SCAN_WINDOW_DAYS, settings.keywordFilter)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val scheduledInstanceIds: StateFlow<Set<String>> = dao.observeAll()
        .map { reminders ->
            reminders.filter { it.status == ReminderStatus.SCHEDULED }.map { it.instanceId }.toSet()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun onPermissionsGranted() {
        CalendarSyncWorker.enqueuePeriodic(getApplication())
        refreshNow()
    }

    fun refreshNow() {
        CalendarSyncWorker.runNow(getApplication())
    }

    fun setLeadMinutes(minutes: Int) {
        viewModelScope.launch {
            preferences.setLeadMinutes(minutes)
            refreshNow()
        }
    }

    fun setKeywordFilter(keyword: String) {
        viewModelScope.launch {
            preferences.setKeywordFilter(keyword)
            refreshNow()
        }
    }
}
