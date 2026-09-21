package com.trainingreminder.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledReminderDao {

    @Query("SELECT * FROM scheduled_reminders")
    fun observeAll(): Flow<List<ScheduledReminder>>

    @Query("SELECT * FROM scheduled_reminders")
    suspend fun getAll(): List<ScheduledReminder>

    @Query("SELECT * FROM scheduled_reminders WHERE instanceId = :instanceId LIMIT 1")
    suspend fun getByInstanceId(instanceId: String): ScheduledReminder?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reminder: ScheduledReminder)

    @Update
    suspend fun update(reminder: ScheduledReminder)

    @Query("DELETE FROM scheduled_reminders WHERE instanceId = :instanceId")
    suspend fun deleteByInstanceId(instanceId: String)

    /** Removes bookkeeping for reminders whose event has already passed, to keep the table small. */
    @Query("DELETE FROM scheduled_reminders WHERE eventStartMillis < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long)
}
