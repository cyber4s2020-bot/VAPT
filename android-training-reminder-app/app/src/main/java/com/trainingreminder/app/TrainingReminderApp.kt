package com.trainingreminder.app

import android.app.Application
import com.trainingreminder.app.data.CalendarRepository
import com.trainingreminder.app.notification.NotificationHelper
import com.trainingreminder.app.sync.CalendarSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class TrainingReminderApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        NotificationHelper.ensureChannels(this)

        // WorkManager backstop: keeps working even if the app process is killed.
        CalendarSyncWorker.enqueuePeriodic(this)

        // While the app process is alive, react to calendar changes (new/edited/deleted
        // events, or a fresh Google Calendar sync) within a couple of seconds instead of
        // waiting for the next 15-minute periodic sync.
        if (CalendarRepository.hasCalendarPermission(this)) {
            CalendarRepository(this)
                .observeChanges()
                .debounce(2_000)
                .onEach { CalendarSyncWorker.runNow(this@TrainingReminderApp) }
                .launchIn(appScope)
        }
    }
}
