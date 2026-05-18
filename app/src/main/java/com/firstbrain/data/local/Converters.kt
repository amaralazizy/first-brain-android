package com.firstbrain.data.local

import androidx.room.TypeConverter
import java.time.Instant

class Converters {
    @TypeConverter
    fun instantToMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun millisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter fun urgencyToString(v: Urgency): String = v.name
    @TypeConverter fun stringToUrgency(v: String): Urgency = Urgency.valueOf(v)

    @TypeConverter fun taskTypeToString(v: TaskType): String = v.name
    @TypeConverter fun stringToTaskType(v: String): TaskType = TaskType.valueOf(v)

    @TypeConverter fun taskStatusToString(v: TaskStatus): String = v.name
    @TypeConverter fun stringToTaskStatus(v: String): TaskStatus = TaskStatus.valueOf(v)

    @TypeConverter fun actionToString(v: InteractionAction): String = v.name
    @TypeConverter fun stringToAction(v: String): InteractionAction = InteractionAction.valueOf(v)
}
