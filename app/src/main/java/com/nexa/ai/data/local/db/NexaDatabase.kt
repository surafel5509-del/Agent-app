package com.nexa.ai.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.nexa.ai.data.local.db.dao.DraftDao
import com.nexa.ai.data.local.db.dao.MessageDao
import com.nexa.ai.data.local.db.dao.SessionDao
import com.nexa.ai.data.local.db.entity.DraftEntity
import com.nexa.ai.data.local.db.entity.MessageEntity
import com.nexa.ai.data.local.db.entity.SessionEntity
import com.nexa.ai.data.local.db.entity.ToolCallEntity

@Database(
    entities = [
        SessionEntity::class,
        MessageEntity::class,
        ToolCallEntity::class,
        DraftEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class NexaDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun draftDao(): DraftDao

    companion object {
        const val NAME = "nexa_ai.db"
    }
}
