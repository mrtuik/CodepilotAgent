package com.example.data.repository

import com.example.data.db.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class ProjectRepository(private val database: CodePilotDatabase) {
    private val projectDao = database.projectDao()
    private val fileDao = database.fileDao()
    private val versionDao = database.versionDao()
    private val changeDao = database.changeDao()
    private val agentRunDao = database.agentRunDao()
    private val settingsDao = database.settingsDao()

    val allProjects: Flow<List<ProjectEntity>> = projectDao.getAllProjects()
    val settings: Flow<SettingsEntity?> = settingsDao.getSettings()

    fun getFiles(projectId: String): Flow<List<FileEntity>> = fileDao.getFilesForProject(projectId)
    fun getVersions(projectId: String): Flow<List<VersionEntity>> = versionDao.getVersionsForProject(projectId)
    fun getChanges(projectId: String): Flow<List<ChangeEntity>> = changeDao.getChangesForProject(projectId)
    fun getAgentRuns(projectId: String): Flow<List<AgentRunEntity>> = agentRunDao.getAgentRunsForProject(projectId)

    suspend fun getProjectById(projectId: String): ProjectEntity? = projectDao.getProjectById(projectId)
    suspend fun getFilesSync(projectId: String): List<FileEntity> = fileDao.getFilesListSync(projectId)
    suspend fun getFileById(id: String): FileEntity? = fileDao.getFileById(id)
    suspend fun getFileByPath(projectId: String, path: String): FileEntity? = fileDao.getFileByPath(projectId, path)
    suspend fun getLatestVersion(projectId: String): VersionEntity? = versionDao.getLatestVersion(projectId)
    suspend fun getSettingsSync(): SettingsEntity? = settingsDao.getSettingsSync()

    suspend fun updateSettings(settings: SettingsEntity) {
        settingsDao.insertSettings(settings)
    }

    suspend fun createProject(name: String, source: String = "ai_studio"): ProjectEntity {
        val projectId = UUID.randomUUID().toString()
        val project = ProjectEntity(
            id = projectId,
            name = name.ifBlank { "Untitled Project" },
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            source = source,
            activeVersionId = ""
        )
        projectDao.insertProject(project)

        // Save last project in settings
        val currentSettings = settingsDao.getSettingsSync() ?: SettingsEntity()
        settingsDao.insertSettings(currentSettings.copy(lastProjectId = projectId))

        return project
    }

    suspend fun updateProject(project: ProjectEntity) {
        projectDao.updateProject(project)
    }

    suspend fun deleteProject(projectId: String) {
        fileDao.deleteFilesForProject(projectId)
        versionDao.deleteVersionsForProject(projectId)
        changeDao.deleteChangesForProject(projectId)
        agentRunDao.deleteRunsForProject(projectId)
        projectDao.deleteProjectById(projectId)
    }

    suspend fun addOrUpdateFile(file: FileEntity, reason: String = "Manual edit"): FileEntity {
        val existing = fileDao.getFileByPath(file.projectId, file.path)
        val fileId = existing?.id ?: file.id.ifBlank { UUID.randomUUID().toString() }
        val beforeContent = existing?.content ?: ""
        val operation = if (existing == null) "CREATE" else "UPDATE"

        val updatedFile = file.copy(
            id = fileId,
            updatedAt = System.currentTimeMillis()
        )
        fileDao.insertFile(updatedFile)

        // Create snapshot version
        val currentFiles = fileDao.getFilesListSync(file.projectId)
        val latestVersion = versionDao.getLatestVersion(file.projectId)
        val nextVersionNum = (latestVersion?.versionNumber ?: 0) + 1
        val versionId = UUID.randomUUID().toString()

        val snapshotJson = serializeFilesSnapshot(currentFiles)
        val version = VersionEntity(
            id = versionId,
            projectId = file.projectId,
            versionNumber = nextVersionNum,
            createdAt = System.currentTimeMillis(),
            label = "Version $nextVersionNum",
            summary = "$operation ${file.name}: $reason",
            snapshotJson = snapshotJson
        )
        versionDao.insertVersion(version)

        val change = ChangeEntity(
            id = UUID.randomUUID().toString(),
            projectId = file.projectId,
            fileId = fileId,
            agentRunId = "",
            versionId = versionId,
            operation = operation,
            path = file.path,
            beforeContent = beforeContent,
            afterContent = file.content,
            reason = reason,
            createdAt = System.currentTimeMillis()
        )
        changeDao.insertChange(change)

        // Update project activeVersionId
        projectDao.getProjectById(file.projectId)?.let { proj ->
            projectDao.updateProject(proj.copy(activeVersionId = versionId, updatedAt = System.currentTimeMillis()))
        }

        return updatedFile
    }

    suspend fun deleteFile(fileId: String, reason: String = "User deleted file") {
        val file = fileDao.getFileById(fileId) ?: return
        fileDao.deleteFileById(fileId)

        val currentFiles = fileDao.getFilesListSync(file.projectId)
        val latestVersion = versionDao.getLatestVersion(file.projectId)
        val nextVersionNum = (latestVersion?.versionNumber ?: 0) + 1
        val versionId = UUID.randomUUID().toString()

        val version = VersionEntity(
            id = versionId,
            projectId = file.projectId,
            versionNumber = nextVersionNum,
            createdAt = System.currentTimeMillis(),
            label = "Version $nextVersionNum",
            summary = "Deleted ${file.name}: $reason",
            snapshotJson = serializeFilesSnapshot(currentFiles)
        )
        versionDao.insertVersion(version)

        val change = ChangeEntity(
            id = UUID.randomUUID().toString(),
            projectId = file.projectId,
            fileId = fileId,
            agentRunId = "",
            versionId = versionId,
            operation = "DELETE",
            path = file.path,
            beforeContent = file.content,
            afterContent = "",
            reason = reason,
            createdAt = System.currentTimeMillis()
        )
        changeDao.insertChange(change)

        projectDao.getProjectById(file.projectId)?.let { proj ->
            projectDao.updateProject(proj.copy(activeVersionId = versionId, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun recordAgentRun(run: AgentRunEntity) {
        agentRunDao.insertRun(run)
    }

    suspend fun applyBatchOperations(
        projectId: String,
        agentRunId: String,
        operations: List<PlannedOperationData>,
        summary: String,
        validationStatus: String
    ): VersionEntity? {
        if (operations.isEmpty()) return null

        val filesToInsert = mutableListOf<FileEntity>()
        val changesToInsert = mutableListOf<ChangeEntity>()
        val versionId = UUID.randomUUID().toString()

        for (op in operations) {
            when (op.operation) {
                "CREATE", "UPDATE", "REPAIR" -> {
                    val existing = fileDao.getFileByPath(projectId, op.path)
                    val fileId = existing?.id ?: UUID.randomUUID().toString()
                    val before = existing?.content ?: ""

                    val ext = op.path.substringAfterLast('.', "")
                    val name = op.path.substringAfterLast('/', op.path)
                    val lang = detectLanguage(ext)

                    val newFile = FileEntity(
                        id = fileId,
                        projectId = projectId,
                        path = op.path,
                        name = name,
                        extension = ext,
                        language = lang,
                        content = op.content,
                        size = op.content.toByteArray().size.toLong(),
                        createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    filesToInsert.add(newFile)

                    changesToInsert.add(
                        ChangeEntity(
                            id = UUID.randomUUID().toString(),
                            projectId = projectId,
                            fileId = fileId,
                            agentRunId = agentRunId,
                            versionId = versionId,
                            operation = op.operation,
                            path = op.path,
                            beforeContent = before,
                            afterContent = op.content,
                            reason = op.reason,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                }
                "DELETE" -> {
                    val existing = fileDao.getFileByPath(projectId, op.path)
                    if (existing != null) {
                        fileDao.deleteFileById(existing.id)
                        changesToInsert.add(
                            ChangeEntity(
                                id = UUID.randomUUID().toString(),
                                projectId = projectId,
                                fileId = existing.id,
                                agentRunId = agentRunId,
                                versionId = versionId,
                                operation = "DELETE",
                                path = op.path,
                                beforeContent = existing.content,
                                afterContent = "",
                                reason = op.reason,
                                createdAt = System.currentTimeMillis()
                            )
                        )
                    }
                }
            }
        }

        if (filesToInsert.isNotEmpty()) {
            fileDao.insertFiles(filesToInsert)
        }
        if (changesToInsert.isNotEmpty()) {
            changeDao.insertChanges(changesToInsert)
        }

        // Snapshot of all current files in project
        val allCurrentFiles = fileDao.getFilesListSync(projectId)
        val latestVer = versionDao.getLatestVersion(projectId)
        val nextVersionNum = (latestVer?.versionNumber ?: 0) + 1

        val version = VersionEntity(
            id = versionId,
            projectId = projectId,
            versionNumber = nextVersionNum,
            createdAt = System.currentTimeMillis(),
            label = "Version $nextVersionNum",
            summary = summary,
            snapshotJson = serializeFilesSnapshot(allCurrentFiles)
        )
        versionDao.insertVersion(version)

        projectDao.getProjectById(projectId)?.let { proj ->
            projectDao.updateProject(proj.copy(activeVersionId = versionId, updatedAt = System.currentTimeMillis()))
        }

        return version
    }

    suspend fun restoreVersion(projectId: String, targetVersionId: String): VersionEntity? {
        val targetVersion = versionDao.getVersionById(targetVersionId) ?: return null
        val restoredFiles = deserializeFilesSnapshot(projectId, targetVersion.snapshotJson)

        // Clear current files and replace with snapshot files
        fileDao.deleteFilesForProject(projectId)
        fileDao.insertFiles(restoredFiles)

        val latestVersion = versionDao.getLatestVersion(projectId)
        val nextVersionNum = (latestVersion?.versionNumber ?: 0) + 1
        val newVersionId = UUID.randomUUID().toString()

        val restoreVersion = VersionEntity(
            id = newVersionId,
            projectId = projectId,
            versionNumber = nextVersionNum,
            createdAt = System.currentTimeMillis(),
            label = "Version $nextVersionNum",
            summary = "Restored from Version ${targetVersion.versionNumber} (\"${targetVersion.label}\")",
            snapshotJson = targetVersion.snapshotJson
        )
        versionDao.insertVersion(restoreVersion)

        // Record change
        changeDao.insertChange(
            ChangeEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                fileId = "restore",
                agentRunId = "",
                versionId = newVersionId,
                operation = "RESTORE",
                path = "project/*",
                beforeContent = "",
                afterContent = "",
                reason = "Restored project snapshot from Version ${targetVersion.versionNumber}",
                createdAt = System.currentTimeMillis()
            )
        )

        projectDao.getProjectById(projectId)?.let { proj ->
            projectDao.updateProject(proj.copy(activeVersionId = newVersionId, updatedAt = System.currentTimeMillis()))
        }

        return restoreVersion
    }

    private fun serializeFilesSnapshot(files: List<FileEntity>): String {
        val jsonArray = JSONArray()
        for (file in files) {
            val obj = JSONObject()
            obj.put("id", file.id)
            obj.put("path", file.path)
            obj.put("name", file.name)
            obj.put("extension", file.extension)
            obj.put("language", file.language)
            obj.put("content", file.content)
            obj.put("size", file.size)
            jsonArray.put(obj)
        }
        return jsonArray.toString()
    }

    private fun deserializeFilesSnapshot(projectId: String, snapshotJson: String): List<FileEntity> {
        val list = mutableListOf<FileEntity>()
        if (snapshotJson.isBlank()) return list
        try {
            val jsonArray = JSONArray(snapshotJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    FileEntity(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        projectId = projectId,
                        path = obj.getString("path"),
                        name = obj.optString("name", obj.getString("path").substringAfterLast('/')),
                        extension = obj.optString("extension", ""),
                        language = obj.optString("language", "text"),
                        content = obj.optString("content", ""),
                        size = obj.optLong("size", 0L),
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    private fun detectLanguage(ext: String): String {
        return when (ext.lowercase()) {
            "html", "htm" -> "html"
            "css" -> "css"
            "js", "javascript" -> "javascript"
            "ts", "typescript" -> "typescript"
            "jsx" -> "jsx"
            "tsx" -> "tsx"
            "json" -> "json"
            "kt", "kts" -> "kotlin"
            "java" -> "java"
            "xml" -> "xml"
            "yaml", "yml" -> "yaml"
            "md" -> "markdown"
            "py" -> "python"
            else -> "text"
        }
    }
}

data class PlannedOperationData(
    val operation: String, // CREATE, UPDATE, DELETE, NO_CHANGE, AMBIGUOUS, REPAIR
    val path: String,
    val content: String,
    val reason: String,
    val confidence: Double = 1.0,
    val ambiguityReason: String? = null
)
