package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.*
import com.example.data.repository.PlannedOperationData
import com.example.data.repository.ProjectRepository
import com.example.engine.*
import com.example.gemini.GeminiModelMode
import com.example.gemini.GeminiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.InputStream
import java.util.UUID

enum class Screen {
    WORKSPACE,
    PLAN,
    ARTIFACTS,
    FILE_VIEWER,
    FILE_DIFF,
    FILES
}

enum class StageStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    WARNING,
    FAILED
}

enum class AgentActivityType {
    THOUGHT,
    SUMMARY_CARD,
    ACTION,
    FILE_READ,
    FILE_CREATE,
    FILE_UPDATE,
    VALIDATE,
    ARTIFACT_READY
}

data class AgentActivityItem(
    val id: String = UUID.randomUUID().toString(),
    val type: AgentActivityType,
    val title: String,
    val targetPath: String? = null,
    val durationSeconds: Int = 0,
    val detailText: String? = null,
    val isExpanded: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class ResponseAgentRun(
    val id: String = UUID.randomUUID().toString(),
    val responseNumber: Int = 1,
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val elapsedSeconds: Int = 0,
    val status: String = "running", // running, completed, warning, failed
    val summary: String = "",
    val activities: List<AgentActivityItem> = emptyList(),
    val changedOperations: List<PlannedOperationData> = emptyList(),
    val filesCreatedCount: Int = 0,
    val filesUpdatedCount: Int = 0
)

data class PlanStageItem(
    val stageNumber: Int,
    val title: String,
    val explanation: String,
    val status: StageStatus = StageStatus.PENDING,
    val timestamp: String = ""
)

data class UiNotification(
    val id: String = UUID.randomUUID().toString(),
    val message: String,
    val isError: Boolean = false
)

data class CodePilotUiState(
    val currentScreen: Screen = Screen.WORKSPACE,
    val activeProject: ProjectEntity? = null,
    val allProjects: List<ProjectEntity> = emptyList(),
    val files: List<FileEntity> = emptyList(),
    val versions: List<VersionEntity> = emptyList(),
    val changes: List<ChangeEntity> = emptyList(),
    val agentRuns: List<AgentRunEntity> = emptyList(),
    val agentStatus: String = "Ready", // Ready, Monitoring, Analysing, Updating, Validating, Warning, Error
    val currentResponseRun: ResponseAgentRun? = null,
    val responseRunsHistory: List<ResponseAgentRun> = emptyList(),
    val hasAiResponseArrived: Boolean = false,
    val planStages: List<PlanStageItem> = emptyList(),
    val plannedOperations: List<PlannedOperationData> = emptyList(),
    val selectedFile: FileEntity? = null,
    val selectedChange: ChangeEntity? = null,
    val selectedDiffResult: DiffResult? = null,
    val validationResult: ValidationResult? = null,
    val repairProposals: List<RepairProposal> = emptyList(),
    val isRepairModalOpen: Boolean = false,
    val isNewProjectDialogOpen: Boolean = false,
    val isApiSettingsOpen: Boolean = false,
    val customApiKey: String = "",
    val modelMode: GeminiModelMode = GeminiModelMode.HIGH_THINKING,
    val notification: UiNotification? = null,
    val autoPlanOpen: Boolean = true,
    val searchQuery: String = "",
    val activeFileFilter: String = "All", // All, Created, Updated, Deleted, Warning, Error
    val isAiProcessing: Boolean = false,
    val pendingAmbiguousOps: List<PlannedOperationData> = emptyList(),
    val isAmbiguityDialogOpen: Boolean = false,
    val isBottomNavExpanded: Boolean = false
)

class CodePilotViewModel(application: Application) : AndroidViewModel(application) {
    private val database = CodePilotDatabase.getDatabase(application)
    private val repository = ProjectRepository(database)
    private val geminiService = GeminiService()

    private val _uiState = MutableStateFlow(CodePilotUiState())
    val uiState: StateFlow<CodePilotUiState> = _uiState.asStateFlow()

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            repository.allProjects.collect { projects ->
                _uiState.update { it.copy(allProjects = projects) }
                if (_uiState.value.activeProject == null && projects.isNotEmpty()) {
                    val settings = repository.getSettingsSync()
                    val target = projects.find { it.id == settings?.lastProjectId } ?: projects.first()
                    setActiveProject(target)
                } else if (projects.isEmpty()) {
                    // Create default starter project so user can start immediately
                    val defaultProj = repository.createProject("My Code Project", "ai_studio")
                    setActiveProject(defaultProj)
                }
            }
        }
    }

    fun setActiveProject(project: ProjectEntity) {
        _uiState.update { it.copy(activeProject = project) }
        viewModelScope.launch {
            launch {
                repository.getFiles(project.id).collect { files ->
                    _uiState.update { it.copy(files = files) }
                    // Run continuous light validation
                    val validation = ValidationEngine.validateProject(files)
                    _uiState.update { it.copy(validationResult = validation) }
                }
            }
            launch {
                repository.getVersions(project.id).collect { versions ->
                    _uiState.update { it.copy(versions = versions) }
                }
            }
            launch {
                repository.getChanges(project.id).collect { changes ->
                    _uiState.update { it.copy(changes = changes) }
                }
            }
            launch {
                repository.getAgentRuns(project.id).collect { runs ->
                    _uiState.update { it.copy(agentRuns = runs) }
                }
            }
        }
    }

    fun createProject(name: String) {
        viewModelScope.launch {
            val project = repository.createProject(name, "ai_studio")
            setActiveProject(project)
            showNotification("Created project \"${project.name}\"")
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            repository.deleteProject(projectId)
            showNotification("Project deleted")
            _uiState.update { it.copy(activeProject = null) }
        }
    }

    fun navigate(screen: Screen) {
        _uiState.update { it.copy(currentScreen = screen) }
    }

    fun setModelMode(mode: GeminiModelMode) {
        _uiState.update { it.copy(modelMode = mode) }
        showNotification("Switched to ${if (mode == GeminiModelMode.HIGH_THINKING) "High Thinking (gemini-3.1-pro-preview)" else "Low Latency (gemini-3.1-flash-lite)"}")
    }

    fun setCustomApiKey(key: String) {
        _uiState.update { it.copy(customApiKey = key) }
        showNotification("API key saved")
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setFileFilter(filter: String) {
        _uiState.update { it.copy(activeFileFilter = filter) }
    }

    fun setNewProjectDialogOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isNewProjectDialogOpen = isOpen) }
    }

    fun setApiSettingsOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isApiSettingsOpen = isOpen) }
    }

    fun setRepairModalOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isRepairModalOpen = isOpen) }
    }

    fun toggleBottomNavExpanded() {
        _uiState.update { it.copy(isBottomNavExpanded = !it.isBottomNavExpanded) }
    }

    fun dismissAmbiguityDialog() {
        _uiState.update { state ->
            val remaining = state.pendingAmbiguousOps.drop(1)
            state.copy(
                pendingAmbiguousOps = remaining,
                isAmbiguityDialogOpen = remaining.isNotEmpty()
            )
        }
    }

    fun confirmAmbiguousOperation(op: PlannedOperationData, specifiedPath: String) {
        val activeProj = _uiState.value.activeProject ?: return
        val cleanPath = specifiedPath.trim().removePrefix("/")
        viewModelScope.launch {
            val ext = cleanPath.substringAfterLast('.', "")
            val name = cleanPath.substringAfterLast('/', cleanPath)
            val lang = when (ext.lowercase()) {
                "html" -> "html"
                "css" -> "css"
                "js" -> "javascript"
                "ts" -> "typescript"
                "json" -> "json"
                "kt" -> "kotlin"
                else -> "text"
            }
            val file = FileEntity(
                id = UUID.randomUUID().toString(),
                projectId = activeProj.id,
                path = cleanPath,
                name = name,
                extension = ext,
                language = lang,
                content = op.content,
                size = op.content.toByteArray().size.toLong()
            )
            val reason = "User specified path for AI generated file: $cleanPath"
            repository.addOrUpdateFile(file, reason)

            // Automatically run validation
            val currentFiles = repository.getFilesSync(activeProj.id)
            val validation = ValidationEngine.validateProject(currentFiles)

            _uiState.update { state ->
                val remaining = state.pendingAmbiguousOps.filterNot { it == op }
                state.copy(
                    validationResult = validation,
                    pendingAmbiguousOps = remaining,
                    isAmbiguityDialogOpen = remaining.isNotEmpty(),
                    currentScreen = Screen.ARTIFACTS
                )
            }
            showNotification("Created file $cleanPath")
        }
    }

    fun showNotification(message: String, isError: Boolean = false) {
        _uiState.update { it.copy(notification = UiNotification(message = message, isError = isError)) }
    }

    fun dismissNotification() {
        _uiState.update { it.copy(notification = null) }
    }

    fun viewFile(file: FileEntity) {
        _uiState.update { it.copy(selectedFile = file, currentScreen = Screen.FILE_VIEWER) }
    }

    fun viewDiff(change: ChangeEntity) {
        val diffResult = DiffEngine.computeDiff(change.beforeContent, change.afterContent, change.path)
        _uiState.update {
            it.copy(
                selectedChange = change,
                selectedDiffResult = diffResult,
                currentScreen = Screen.FILE_DIFF
            )
        }
    }

    fun viewDiffForFile(file: FileEntity) {
        val lastChange = _uiState.value.changes.find { it.fileId == file.id }
        val before = lastChange?.beforeContent ?: ""
        val diffResult = DiffEngine.computeDiff(before, file.content, file.path)
        val dummyChange = lastChange ?: ChangeEntity(
            id = UUID.randomUUID().toString(),
            projectId = file.projectId,
            fileId = file.id,
            agentRunId = "",
            versionId = "",
            operation = "CURRENT",
            path = file.path,
            beforeContent = before,
            afterContent = file.content,
            reason = "Current file view",
            createdAt = file.updatedAt
        )
        _uiState.update {
            it.copy(
                selectedChange = dummyChange,
                selectedDiffResult = diffResult,
                currentScreen = Screen.FILE_DIFF
            )
        }
    }

    // Last payload handed to processAiResponse, used to collapse streaming duplicates
    private var lastProcessedRawResponse: String? = null

    // PRIMARY PRODUCT FLOW: fully automatic processing of responses captured from the AI Studio WebView bridge
    fun processAiResponse(rawResponse: String, source: String = "ai_studio_bridge") {
        val activeProj = _uiState.value.activeProject ?: return
        if (rawResponse.isBlank()) {
            showNotification("No response content to analyze", isError = true)
            return
        }

        // Guard against streaming duplicates: the bridge can deliver growing snapshots of the
        // same response. Update the in-flight run instead of starting a new one / re-navigating.
        val previousRaw = lastProcessedRawResponse
        val activeRun = _uiState.value.currentResponseRun
        if (previousRaw != null && activeRun != null) {
            val isContinuation = rawResponse == previousRaw ||
                rawResponse.contains(previousRaw) ||
                previousRaw.contains(rawResponse)
            val runInProgress = activeRun.status == "running" || _uiState.value.isAiProcessing
            val recent = System.currentTimeMillis() - (activeRun.completedAt ?: activeRun.startedAt) < 5000
            if (isContinuation && (runInProgress || recent)) {
                lastProcessedRawResponse = rawResponse
                if (runInProgress) {
                    _uiState.update {
                        it.copy(currentResponseRun = activeRun.copy(summary = activeRun.summary))
                    }
                }
                return
            }
        }
        lastProcessedRawResponse = rawResponse

        viewModelScope.launch(Dispatchers.Default) {
            val agentRunId = UUID.randomUUID().toString()
            val runNumber = _uiState.value.responseRunsHistory.size + 1
            val startTime = System.currentTimeMillis()

            var run = ResponseAgentRun(
                id = agentRunId,
                responseNumber = runNumber,
                startedAt = startTime,
                status = "running",
                summary = "Analyzing AI Studio response #$runNumber..."
            )

            // Auto-open Plan view for THIS response only
            _uiState.update {
                it.copy(
                    hasAiResponseArrived = true,
                    isAiProcessing = true,
                    agentStatus = "Planning",
                    currentScreen = Screen.PLAN,
                    currentResponseRun = run
                )
            }

            fun appendActivity(item: AgentActivityItem) {
                run = run.copy(activities = run.activities + item)
                _uiState.update { it.copy(currentResponseRun = run) }
            }

            // Step 1: Observable Thought step
            appendActivity(
                AgentActivityItem(
                    type = AgentActivityType.THOUGHT,
                    title = "Thought for 3s",
                    durationSeconds = 3
                )
            )
            delay(200)

            // Step 2: Parse response structure
            val candidates = ResponseParser.parse(rawResponse)
            if (candidates.isEmpty()) {
                appendActivity(
                    AgentActivityItem(
                        type = AgentActivityType.SUMMARY_CARD,
                        title = "Observation",
                        detailText = "No code fences or file blocks detected in this response."
                    )
                )
                run = run.copy(
                    status = "warning",
                    completedAt = System.currentTimeMillis(),
                    elapsedSeconds = 1
                )
                _uiState.update {
                    it.copy(
                        isAiProcessing = false,
                        agentStatus = "Warning",
                        currentResponseRun = run,
                        responseRunsHistory = it.responseRunsHistory + run
                    )
                }
                showNotification("No code blocks detected in response", isError = true)
                return@launch
            }

            // Step 3: Observable Goal Summary Card
            val summaryGoal = "Detected ${candidates.size} file candidate(s). Inspecting project and synchronizing safe file changes."
            appendActivity(
                AgentActivityItem(
                    type = AgentActivityType.SUMMARY_CARD,
                    title = "Execution Goal",
                    detailText = summaryGoal
                )
            )
            delay(200)

            // Step 4: Inspect project structure
            appendActivity(
                AgentActivityItem(
                    type = AgentActivityType.ACTION,
                    title = "Inspect project structure safely"
                )
            )
            delay(150)

            // Step 5: Read existing relevant files
            val existingFiles = repository.getFilesSync(activeProj.id)
            val filesToRead = existingFiles.take(3)
            for (f in filesToRead) {
                appendActivity(
                    AgentActivityItem(
                        type = AgentActivityType.FILE_READ,
                        title = "Read",
                        targetPath = f.name
                    )
                )
                delay(100)
            }

            // Step 6: Plan operations
            _uiState.update { it.copy(agentStatus = "Analysing") }
            val plannedOps = FileOperationPlanner.planOperations(candidates, existingFiles)
            _uiState.update { it.copy(plannedOperations = plannedOps) }

            // Step 7: Apply file creations/updates (observable file actions)
            _uiState.update { it.copy(agentStatus = "Updating") }
            val safeOps = plannedOps.filter { it.operation in listOf("CREATE", "UPDATE", "DELETE") }

            for (op in safeOps) {
                val actType = if (op.operation == "CREATE") AgentActivityType.FILE_CREATE else AgentActivityType.FILE_UPDATE
                val actLabel = if (op.operation == "CREATE") "Create" else "Update"
                appendActivity(
                    AgentActivityItem(
                        type = actType,
                        title = actLabel,
                        targetPath = op.path
                    )
                )
                delay(120)
            }

            val summaryText = "AI Studio sync: ${safeOps.count { it.operation == "CREATE" }} created, ${safeOps.count { it.operation == "UPDATE" }} updated"
            val newVersion = repository.applyBatchOperations(
                projectId = activeProj.id,
                agentRunId = agentRunId,
                operations = safeOps,
                summary = summaryText,
                validationStatus = "validating"
            )

            // Step 8: Check references and imports
            appendActivity(
                AgentActivityItem(
                    type = AgentActivityType.ACTION,
                    title = "Check references and import integrity"
                )
            )
            delay(150)

            // Step 9: Validate project
            _uiState.update { it.copy(agentStatus = "Validating") }
            appendActivity(
                AgentActivityItem(
                    type = AgentActivityType.VALIDATE,
                    title = "Validate project workspace"
                )
            )
            delay(150)

            val currentFiles = repository.getFilesSync(activeProj.id)
            val validation = ValidationEngine.validateProject(currentFiles)
            _uiState.update { it.copy(validationResult = validation) }

            // Step 10: Artifact ready step
            val readyLabel = if (safeOps.isNotEmpty()) "Artifact ready — ${safeOps.size} file(s) synchronized" else "Artifact ready — Workspace validated"
            appendActivity(
                AgentActivityItem(
                    type = AgentActivityType.ARTIFACT_READY,
                    title = readyLabel
                )
            )

            val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000).toInt().coerceAtLeast(1)
            val finalStatus = if (validation.status == "error") "Error" else if (validation.status == "warning") "Warning" else "Ready"
            val ambiguousOps = plannedOps.filter { it.operation == "AMBIGUOUS" }

            val completedRun = run.copy(
                status = "completed",
                completedAt = System.currentTimeMillis(),
                elapsedSeconds = elapsedSec,
                summary = summaryText,
                changedOperations = safeOps,
                filesCreatedCount = safeOps.count { it.operation == "CREATE" },
                filesUpdatedCount = safeOps.count { it.operation == "UPDATE" }
            )

            _uiState.update {
                it.copy(
                    agentStatus = finalStatus,
                    isAiProcessing = false,
                    currentResponseRun = completedRun,
                    responseRunsHistory = it.responseRunsHistory + completedRun,
                    pendingAmbiguousOps = ambiguousOps,
                    isAmbiguityDialogOpen = ambiguousOps.isNotEmpty()
                )
            }

            // Record Agent Run in database
            val dbRun = AgentRunEntity(
                id = agentRunId,
                projectId = activeProj.id,
                source = source,
                status = finalStatus.lowercase(),
                startedAt = startTime,
                completedAt = System.currentTimeMillis(),
                summary = summaryText,
                operationsJson = plannedOps.joinToString { "${it.operation}: ${it.path}" },
                validationStatus = validation.status
            )
            repository.recordAgentRun(dbRun)

            showNotification(
                if (safeOps.isNotEmpty()) "Completed: ${safeOps.size} file(s) synchronized"
                else "Response analyzed: no file changes needed"
            )
        }
    }

    private fun updateStage(stageNumber: Int, status: StageStatus, explanation: String) {
        _uiState.update { state ->
            val updated = state.planStages.map { stage ->
                if (stage.stageNumber == stageNumber) {
                    stage.copy(
                        status = status,
                        explanation = explanation,
                        timestamp = if (status == StageStatus.COMPLETED || status == StageStatus.WARNING) "Now" else ""
                    )
                } else stage
            }
            state.copy(planStages = updated)
        }
    }

    // Direct Gemini Assist / Repair prompt invocation
    fun sendDirectAiPrompt(prompt: String) {
        val activeProj = _uiState.value.activeProject ?: return
        if (prompt.isBlank()) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isAiProcessing = true,
                    agentStatus = "Analysing",
                    currentScreen = Screen.PLAN
                )
            }
            updateStage(1, StageStatus.IN_PROGRESS, "Sending request to Gemini (${_uiState.value.modelMode})...")

            val currentFiles = repository.getFilesSync(activeProj.id)
            val filesContext = currentFiles.take(15).joinToString("\n\n") { f ->
                "// File: ${f.path}\n${f.content.take(1500)}"
            }
            val sysInstruction = """
                You are CodePilot Agent assistant.
                You manage a local developer project.
                Output clean code blocks with clear relative paths in comments or headers, for example:
                ```html index.html
                ...
                ```
                Current project files:
                $filesContext
            """.trimIndent()

            val result = geminiService.generateCodeOrAnalysis(
                prompt = prompt,
                mode = _uiState.value.modelMode,
                systemInstruction = sysInstruction,
                customApiKey = _uiState.value.customApiKey
            )

            result.onSuccess { responseText ->
                updateStage(1, StageStatus.COMPLETED, "Received response from Gemini.")
                processAiResponse(responseText, "gemini_api")
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isAiProcessing = false,
                        agentStatus = "Error"
                    )
                }
                updateStage(1, StageStatus.FAILED, "Gemini Error: ${err.message}")
                showNotification("Gemini request failed: ${err.message}", isError = true)
            }
        }
    }

    // Repair Agent
    fun runRepairAnalysis() {
        val activeProj = _uiState.value.activeProject ?: return
        viewModelScope.launch {
            val currentFiles = repository.getFilesSync(activeProj.id)
            val proposals = ProjectRepairEngine.analyzeAndPropose(currentFiles)
            if (proposals.isEmpty()) {
                _uiState.update {
                    it.copy(
                        repairProposals = emptyList(),
                        isRepairModalOpen = false
                    )
                }
                showNotification("Project is healthy: No broken references or missing files found.")
            } else {
                _uiState.update {
                    it.copy(
                        repairProposals = proposals,
                        isRepairModalOpen = true
                    )
                }
            }
        }
    }

    fun applyRepairProposals(selectedProposals: List<RepairProposal>) {
        val activeProj = _uiState.value.activeProject ?: return
        if (selectedProposals.isEmpty()) {
            _uiState.update { it.copy(isRepairModalOpen = false) }
            return
        }

        viewModelScope.launch {
            val ops = selectedProposals.map { p ->
                PlannedOperationData(
                    operation = "CREATE",
                    path = p.targetPath,
                    content = p.proposedContent,
                    reason = p.reason
                )
            }
            val runId = UUID.randomUUID().toString()
            repository.applyBatchOperations(
                projectId = activeProj.id,
                agentRunId = runId,
                operations = ops,
                summary = "Project repair: created ${ops.size} stub file(s)",
                validationStatus = "ready"
            )
            // Automatic validation after repair
            val currentFiles = repository.getFilesSync(activeProj.id)
            val validation = ValidationEngine.validateProject(currentFiles)
            _uiState.update {
                it.copy(
                    validationResult = validation,
                    isRepairModalOpen = false,
                    repairProposals = emptyList(),
                    agentStatus = "Ready"
                )
            }
            showNotification("Applied ${ops.size} repair proposal(s)")
        }
    }

    // Manual add/update file with automatic validation
    fun addOrUpdateFile(path: String, content: String, reason: String = "Manual file edit") {
        val activeProj = _uiState.value.activeProject ?: return
        viewModelScope.launch {
            val ext = path.substringAfterLast('.', "")
            val name = path.substringAfterLast('/', path)
            val lang = when (ext.lowercase()) {
                "html" -> "html"
                "css" -> "css"
                "js" -> "javascript"
                "ts" -> "typescript"
                "json" -> "json"
                "kt" -> "kotlin"
                else -> "text"
            }
            val file = FileEntity(
                id = UUID.randomUUID().toString(),
                projectId = activeProj.id,
                path = path,
                name = name,
                extension = ext,
                language = lang,
                content = content,
                size = content.toByteArray().size.toLong()
            )
            repository.addOrUpdateFile(file, reason)

            // Automatic validation
            val currentFiles = repository.getFilesSync(activeProj.id)
            val validation = ValidationEngine.validateProject(currentFiles)
            _uiState.update { it.copy(validationResult = validation) }
            showNotification("Saved file $path")
        }
    }

    fun deleteFile(fileId: String) {
        val activeProj = _uiState.value.activeProject ?: return
        viewModelScope.launch {
            repository.deleteFile(fileId)
            // Automatic validation
            val currentFiles = repository.getFilesSync(activeProj.id)
            val validation = ValidationEngine.validateProject(currentFiles)
            _uiState.update { it.copy(validationResult = validation) }
            showNotification("File deleted")
            if (_uiState.value.selectedFile?.id == fileId) {
                _uiState.update { it.copy(selectedFile = null, currentScreen = Screen.FILES) }
            }
        }
    }

    fun restoreVersion(versionId: String) {
        val activeProj = _uiState.value.activeProject ?: return
        viewModelScope.launch {
            val restored = repository.restoreVersion(activeProj.id, versionId)
            if (restored != null) {
                // Automatic validation
                val currentFiles = repository.getFilesSync(activeProj.id)
                val validation = ValidationEngine.validateProject(currentFiles)
                _uiState.update { it.copy(validationResult = validation, currentScreen = Screen.ARTIFACTS) }
                showNotification("Restored to Version ${restored.versionNumber}")
            }
        }
    }

    fun importZip(inputStream: InputStream) {
        val activeProj = _uiState.value.activeProject ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val imported = ZipManager.parseZipArchive(inputStream)
                if (imported.isEmpty()) {
                    showNotification("No readable files in ZIP archive", isError = true)
                    return@launch
                }
                val ops = imported.map { item ->
                    PlannedOperationData(
                        operation = "CREATE",
                        path = item.path,
                        content = item.content,
                        reason = "Imported from ZIP archive"
                    )
                }
                repository.applyBatchOperations(
                    projectId = activeProj.id,
                    agentRunId = UUID.randomUUID().toString(),
                    operations = ops,
                    summary = "Imported ${ops.size} file(s) from ZIP",
                    validationStatus = "ready"
                )
                // Automatic validation
                val currentFiles = repository.getFilesSync(activeProj.id)
                val validation = ValidationEngine.validateProject(currentFiles)
                showNotification("Imported ${ops.size} file(s) from ZIP archive")
                _uiState.update { it.copy(validationResult = validation, currentScreen = Screen.ARTIFACTS) }
            } catch (e: Exception) {
                showNotification("Failed to import ZIP: ${e.message}", isError = true)
            }
        }
    }

    fun getExportZipBytes(): ByteArray {
        val files = _uiState.value.files
        return ZipManager.createZipArchive(files)
    }

    fun runManualValidation() {
        val files = _uiState.value.files
        val validation = ValidationEngine.validateProject(files)
        _uiState.update { it.copy(validationResult = validation) }
        showNotification(
            if (validation.status == "ready") "Build-ready checks passed."
            else "Checks completed with ${validation.status.uppercase()} status."
        )
    }
}
