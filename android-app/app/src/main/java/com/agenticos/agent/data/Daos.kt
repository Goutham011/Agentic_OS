package com.agenticos.agent.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import androidx.room.Transaction
import com.agenticos.agent.models.AutomationRule
import com.agenticos.agent.models.ConversationMemory
import com.agenticos.agent.models.UserPreference
import kotlinx.coroutines.flow.Flow

@Dao
interface AutomationRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: AutomationRule): Long

    @Update
    suspend fun update(rule: AutomationRule)

    @Delete
    suspend fun delete(rule: AutomationRule)

    @Query("SELECT * FROM automation_rules WHERE enabled = 1 ORDER BY createdAt DESC")
    fun getEnabledRules(): Flow<List<AutomationRule>>

    @Query("SELECT * FROM automation_rules ORDER BY createdAt DESC")
    fun getAllRules(): Flow<List<AutomationRule>>

    @Query("SELECT * FROM automation_rules WHERE id = :id")
    suspend fun getById(id: Long): AutomationRule?

    @Query("DELETE FROM automation_rules WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface ConversationMemoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(memory: ConversationMemory)

    @Query("SELECT * FROM conversation_memory ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<ConversationMemory>

    @Query("DELETE FROM conversation_memory WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("DELETE FROM conversation_memory")
    suspend fun clearAll()
}

@Dao
interface UserPreferenceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(preference: UserPreference)

    @Query("SELECT * FROM user_preferences WHERE key = :key")
    suspend fun get(key: String): UserPreference?

    @Query("SELECT * FROM user_preferences")
    suspend fun getAll(): List<UserPreference>

    @Query("DELETE FROM user_preferences WHERE key = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM user_preferences")
    suspend fun clearAll()
}