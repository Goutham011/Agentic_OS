package com.agenticos.agent.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.agenticos.agent.models.AutomationRule
import com.agenticos.agent.models.ConversationMemory
import com.agenticos.agent.models.UserPreference
import com.agenticos.agent.utils.Converters
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

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
        private const val DB_NAME = "agentic_database"
        
        /**
         * Get database passphrase derived from device credentials.
         * In production, this should use BiometricPrompt or AccountManager
         * to derive a secure key from user authentication.
         */
        fun getPassphrase(context: Context): ByteArray {
            // Try to use hardware-backed keystore or derived key
            // For now, use a stable hash based on device fingerprint
            val fingerprint = getDeviceFingerprint(context)
            return SQLiteDatabase.getBytes(fingerprint.toCharArray())
        }
        
        private fun getDeviceFingerprint(context: Context): String {
            // Security Note: In production, use BiometricPrompt to derive key from user biometrics
            // This is a simplified approach for development
            val androidId = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            )
            return "agentic_os_db_key_${androidId ?: "fallback_key"}"
        }
        
        fun getInstance(context: Context): AgenticDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }
        
        private fun buildDatabase(context: Context): AgenticDatabase {
            try {
                val passphrase = getPassphrase(context)
                val factory = SupportFactory(passphrase)
                
                return Room.databaseBuilder(
                    context.applicationContext,
                    AgenticDatabase::class.java,
                    DB_NAME
                )
                    .openHelperFactory(factory)
                    .fallbackToDestructiveMigration()
                    .build()
            } catch (e: Exception) {
                Log.e("AgenticDatabase", "Failed to create encrypted database", e)
                // Fallback to in-memory database for testing
                return Room.inMemoryDatabaseBuilder(context.applicationContext, AgenticDatabase::class.java)
                    .allowMainThreadQueries()
                    .fallbackToDestructiveMigration()
                    .build()
            }
        }
    }
}