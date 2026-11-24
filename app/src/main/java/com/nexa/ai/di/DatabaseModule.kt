package com.nexa.ai.di

import android.content.Context
import androidx.room.Room
import com.nexa.ai.data.local.db.NexaDatabase
import com.nexa.ai.data.local.db.dao.DraftDao
import com.nexa.ai.data.local.db.dao.MessageDao
import com.nexa.ai.data.local.db.dao.SessionDao
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
    fun provideDatabase(@ApplicationContext context: Context): NexaDatabase =
        Room.databaseBuilder(context, NexaDatabase::class.java, NexaDatabase.NAME)
            // v1 of the cache: a destructive rebuild is acceptable because every row can be
            // re-fetched from the gateway. Real migrations arrive with the next schema bump.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideSessionDao(database: NexaDatabase): SessionDao = database.sessionDao()

    @Provides
    fun provideMessageDao(database: NexaDatabase): MessageDao = database.messageDao()

    @Provides
    fun provideDraftDao(database: NexaDatabase): DraftDao = database.draftDao()
}
