package com.firstbrain.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [TaskEntity::class, InteractionEntity::class, FeedbackOutboxEntity::class],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun interactionDao(): InteractionDao
    abstract fun feedbackOutboxDao(): FeedbackOutboxDao

    companion object {
        const val NAME = "first_brain.db"
    }
}
