package com.agenticos.agent.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.agenticos.agent.models.AutomationRule
import com.agenticos.agent.models.ConversationMemory
import com.agenticos.agent.models.UserPreference
import com.agenticos.agent.utils.Converters

@Database(
    entities = [
        AutomationRule::class,
        ConversationMemory::class,
        UserPreference::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AgenticDatabase : RoomDatabase() {
    abstract fun automationRuleDao(): AutomationRuleDao
    abstract fun conversationMemoryDao(): ConversationMemoryDao
    abstract fun userPreferenceDao(): UserPreferenceDao

    companion object {
        @Volatile private var INSTANCE: AgenticDatabase? = null

        fun getInstance(context: Context): AgenticDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AgenticDatabase::class.java,
                    "agentic_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}