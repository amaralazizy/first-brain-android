package com.firstbrain.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.firstbrain.data.local.AppDatabase
import com.firstbrain.data.local.InteractionDao
import com.firstbrain.data.local.TaskDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, AppDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun taskDao(db: AppDatabase): TaskDao = db.taskDao()
    @Provides fun interactionDao(db: AppDatabase): InteractionDao = db.interactionDao()

    @Provides
    @Singleton
    fun workManager(@ApplicationContext ctx: Context): WorkManager =
        WorkManager.getInstance(ctx)
}
