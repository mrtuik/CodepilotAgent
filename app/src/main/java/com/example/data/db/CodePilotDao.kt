package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: String): ProjectEntity?

    @Query("SELECT * FROM projects WHERE chatId = :chatId LIMIT 1")
    suspend fun getProjectByChatId(chatId: String): ProjectEntity?

    @Query("UPDATE projects SET name = :name, isNameAuto = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun renameProject(id: String, name: String, updatedAt: Long)


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity)

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProjectById(id: String)
}

@Dao
interface FileDao {
    @Query("SELECT * FROM files WHERE projectId = :projectId ORDER BY path ASC")
    fun getFilesForProject(projectId: String): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE projectId = :projectId")
    suspend fun getFilesListSync(projectId: String): List<FileEntity>

    @Query("SELECT * FROM files WHERE projectId = :projectId AND path = :path LIMIT 1")
    suspend fun getFileByPath(projectId: String, path: String): FileEntity?

    @Query("SELECT * FROM files WHERE id = :id LIMIT 1")
    suspend fun getFileById(id: String): FileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: FileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<FileEntity>)

    @Update
    suspend fun updateFile(file: FileEntity)

    @Query("DELETE FROM files WHERE id = :id")
    suspend fun deleteFileById(id: String)

    @Query("DELETE FROM files WHERE projectId = :projectId")
    suspend fun deleteFilesForProject(projectId: String)
}

@Dao
interface VersionDao {
    @Query("SELECT * FROM versions WHERE projectId = :projectId ORDER BY versionNumber DESC")
    fun getVersionsForProject(projectId: String): Flow<List<VersionEntity>>

    @Query("SELECT * FROM versions WHERE projectId = :projectId ORDER BY versionNumber DESC LIMIT 1")
    suspend fun getLatestVersion(projectId: String): VersionEntity?

    @Query("SELECT * FROM versions WHERE id = :id LIMIT 1")
    suspend fun getVersionById(id: String): VersionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVersion(version: VersionEntity)

    @Query("DELETE FROM versions WHERE projectId = :projectId")
    suspend fun deleteVersionsForProject(projectId: String)
}

@Dao
interface ChangeDao {
    @Query("SELECT * FROM changes WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun getChangesForProject(projectId: String): Flow<List<ChangeEntity>>

    @Query("SELECT * FROM changes WHERE versionId = :versionId ORDER BY createdAt DESC")
    fun getChangesForVersion(versionId: String): Flow<List<ChangeEntity>>

    @Query("SELECT * FROM changes WHERE versionId = :versionId")
    suspend fun getChangesForVersionSync(versionId: String): List<ChangeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChange(change: ChangeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChanges(changes: List<ChangeEntity>)

    @Query("DELETE FROM changes WHERE projectId = :projectId")
    suspend fun deleteChangesForProject(projectId: String)
}

@Dao
interface AgentRunDao {
    @Query("SELECT * FROM agent_runs WHERE projectId = :projectId ORDER BY startedAt DESC")
    fun getAgentRunsForProject(projectId: String): Flow<List<AgentRunEntity>>

    @Query("SELECT * FROM agent_runs WHERE projectId = :projectId ORDER BY startedAt DESC LIMIT 1")
    suspend fun getLatestRun(projectId: String): AgentRunEntity?

    @Query("SELECT * FROM agent_runs WHERE id = :id LIMIT 1")
    suspend fun getRunById(id: String): AgentRunEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRun(run: AgentRunEntity)

    @Update
    suspend fun updateRun(run: AgentRunEntity)

    @Query("DELETE FROM agent_runs WHERE projectId = :projectId")
    suspend fun deleteRunsForProject(projectId: String)
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 1 LIMIT 1")
    fun getSettings(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = 1 LIMIT 1")
    suspend fun getSettingsSync(): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: SettingsEntity)
}
