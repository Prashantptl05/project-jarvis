package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val fact: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "missions")
data class MissionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val priority: String = "NORMAL",
    val dueTime: String = "",
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String, // "USER", "JARVIS", "SYSTEM"
    val content: String,
    val toolCallLog: String? = null,
    val imagePath: String? = null,
    val modelBadge: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY updatedAt DESC")
    fun observeAllMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun getRecentMemories(limit: Int = 25): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE LOWER(fact) LIKE '%' || LOWER(:query) || '%' OR LOWER(category) LIKE '%' || LOWER(:query) || '%' ORDER BY updatedAt DESC")
    suspend fun searchMemories(query: String): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity): Long

    @Update
    suspend fun updateMemory(memory: MemoryEntity)

    @Delete
    suspend fun deleteMemory(memory: MemoryEntity)

    @Query("DELETE FROM memories")
    suspend fun clearAllMemories()
}

@Dao
interface MissionDao {
    @Query("SELECT * FROM missions ORDER BY isCompleted ASC, CASE priority WHEN 'CRITICAL' THEN 0 WHEN 'HIGH' THEN 1 WHEN 'NORMAL' THEN 2 ELSE 3 END, createdAt DESC")
    fun observeAllMissions(): Flow<List<MissionEntity>>

    @Query("SELECT * FROM missions WHERE isCompleted = 0 ORDER BY CASE priority WHEN 'CRITICAL' THEN 0 WHEN 'HIGH' THEN 1 WHEN 'NORMAL' THEN 2 ELSE 3 END, createdAt DESC")
    suspend fun getPendingMissions(): List<MissionEntity>

    @Query("SELECT * FROM missions ORDER BY isCompleted ASC, createdAt DESC")
    suspend fun getAllMissionsSnapshot(): List<MissionEntity>

    @Query("SELECT * FROM missions WHERE LOWER(title) LIKE '%' || LOWER(:titleQuery) || '%' LIMIT 1")
    suspend fun findMissionByTitle(titleQuery: String): MissionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMission(mission: MissionEntity): Long

    @Update
    suspend fun updateMission(mission: MissionEntity)

    @Delete
    suspend fun deleteMission(mission: MissionEntity)

    @Query("DELETE FROM missions WHERE isCompleted = 1")
    suspend fun clearCompletedMissions()
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC, id ASC")
    fun observeAllMessages(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp DESC, id DESC LIMIT :limit")
    suspend fun getRecentMessages(limit: Int = 14): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages")
    suspend fun clearAllMessages()
}

@Database(
    entities = [MemoryEntity::class, MissionEntity::class, ChatMessageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class JarvisDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun missionDao(): MissionDao
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        @Volatile
        private var INSTANCE: JarvisDatabase? = null

        fun getInstance(context: Context): JarvisDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    JarvisDatabase::class.java,
                    "jarvis_neural_core.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

class JarvisLocalRepository(private val database: JarvisDatabase) {
    private val memoryDao = database.memoryDao()
    private val missionDao = database.missionDao()
    private val chatDao = database.chatMessageDao()

    val memories: Flow<List<MemoryEntity>> = memoryDao.observeAllMemories()
    val missions: Flow<List<MissionEntity>> = missionDao.observeAllMissions()
    val messages: Flow<List<ChatMessageEntity>> = chatDao.observeAllMessages()

    suspend fun getRelevantMemories(userPrompt: String, maxCount: Int = 12): List<MemoryEntity> {
        val all = memoryDao.getRecentMemories(40)
        if (all.size <= maxCount) return all
        val tokens = userPrompt.lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 3 }
            .toSet()
        if (tokens.isEmpty()) return all.take(maxCount)

        val scored = all.sortedByDescending { mem ->
            val haystack = "${mem.category} ${mem.fact}".lowercase()
            val matchCount = tokens.count { haystack.contains(it) }
            val profileBoost = if (mem.category.equals("PROFILE", ignoreCase = true)) 2 else 0
            matchCount * 3 + profileBoost
        }
        return scored.take(maxCount)
    }

    suspend fun saveMemory(category: String, fact: String): MemoryEntity {
        val normalizedCat = category.trim().uppercase().ifBlank { "INTEL" }
        val cleanFact = fact.trim()
        val existing = memoryDao.getRecentMemories(60).firstOrNull {
            it.fact.equals(cleanFact, ignoreCase = true)
        }
        return if (existing != null) {
            val updated = existing.copy(
                category = normalizedCat,
                updatedAt = System.currentTimeMillis()
            )
            memoryDao.updateMemory(updated)
            updated
        } else {
            val entity = MemoryEntity(category = normalizedCat, fact = cleanFact)
            val id = memoryDao.insertMemory(entity)
            entity.copy(id = id)
        }
    }

    suspend fun updateMemory(memory: MemoryEntity) {
        memoryDao.updateMemory(memory.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteMemory(memory: MemoryEntity) = memoryDao.deleteMemory(memory)

    suspend fun wipeAllMemories() = memoryDao.clearAllMemories()

    suspend fun getPendingMissions(): List<MissionEntity> = missionDao.getPendingMissions()

    suspend fun getAllMissionsSnapshot(): List<MissionEntity> = missionDao.getAllMissionsSnapshot()

    suspend fun findMissionByTitle(title: String): MissionEntity? = missionDao.findMissionByTitle(title)

    suspend fun addMission(title: String, priority: String, dueTime: String): MissionEntity {
        val normalizedPriority = when (priority.trim().uppercase()) {
            "CRITICAL", "URGENT" -> "CRITICAL"
            "HIGH" -> "HIGH"
            "LOW" -> "LOW"
            else -> "NORMAL"
        }
        val entity = MissionEntity(
            title = title.trim(),
            priority = normalizedPriority,
            dueTime = dueTime.trim()
        )
        val id = missionDao.insertMission(entity)
        return entity.copy(id = id)
    }

    suspend fun toggleMissionCompleted(mission: MissionEntity) {
        missionDao.updateMission(mission.copy(isCompleted = !mission.isCompleted))
    }

    suspend fun completeMissionByTitle(title: String): MissionEntity? {
        val target = missionDao.findMissionByTitle(title.trim()) ?: return null
        val updated = target.copy(isCompleted = true)
        missionDao.updateMission(updated)
        return updated
    }

    suspend fun deleteMission(mission: MissionEntity) = missionDao.deleteMission(mission)

    suspend fun clearCompletedMissions() = missionDao.clearCompletedMissions()

    suspend fun getRecentChatHistory(limit: Int = 12): List<ChatMessageEntity> {
        return chatDao.getRecentMessages(limit).reversed()
    }

    suspend fun insertMessage(message: ChatMessageEntity): Long = chatDao.insertMessage(message)

    suspend fun clearChatHistory() = chatDao.clearAllMessages()
}
