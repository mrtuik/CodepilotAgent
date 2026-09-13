package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val source: String = "ai_studio",
    val activeVersionId: String = "",
    // Identifier of the AI Studio chat this project is bound to (empty = not bound yet)
    val chatId: String = "",
    // True while the name is still the auto placeholder and may be replaced by a detected app name
    val isNameAuto: Boolean = true
)


@Entity(tableName = "files")
data class FileEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val path: String,
    val name: String,
    val extension: String,
    val language: String,
    val content: String,
    val size: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val hash: String = ""
)

@Entity(tableName = "versions")
data class VersionEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val versionNumber: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val label: String,
    val summary: String,
    val snapshotJson: String // Serialized map or list of files
)

@Entity(tableName = "changes")
data class ChangeEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val fileId: String,
    val agentRunId: String,
    val versionId: String,
    val operation: String, // CREATE, UPDATE, DELETE, REPAIR, NO_CHANGE
    val path: String,
    val beforeContent: String,
    val afterContent: String,
    val reason: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "agent_runs")
data class AgentRunEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val source: String, // auto_bridge, manual_paste, gemini_api, repair
    val status: String, // planning, analysing, updating, validating, ready, warning, error
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long = 0L,
    val summary: String,
    val operationsJson: String,
    val validationStatus: String = "ready"
)

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val autoPlanOpen: Boolean = true,
    val compactControls: Boolean = true,
    val lastProjectId: String = "",
    val modelMode: String = "high_thinking" // "high_thinking" or "low_latency"
)
