package com.trainingreminder.app.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromStatus(status: ReminderStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): ReminderStatus = ReminderStatus.valueOf(value)
}
