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
import kotlinx.coroutines.Job
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
    PROMPT_SENT,
    THOUGHT,
    SUMMARY_CARD,
    ACTION,
    FILE_READ,
    FILE_CREATE,
    FILE_UPDATE,
    HTML_FILE_CARD,
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
                    val defaultProj = repository.createProject(DEFAULT_PROJECT_NAME, "ai_studio")
                    setActiveProject(defaultProj)
                }
            }
        }
    }

    // Collectors of the currently active project, cancelled whenever the active project switches
    private var activeProjectJob: Job? = null

    fun setActiveProject(project: ProjectEntity) {
        _uiState.update { it.copy(activeProject = project) }
        activeProjectJob?.cancel()
        activeProjectJob = viewModelScope.launch {
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

    // ---------------------------------------------------------------------
    // AI Studio chat scoping: one chat = one project (own plan history + zip)
    // ---------------------------------------------------------------------

    private var activeChatId: String = ""

    /**
     * Called from the WebView bridge whenever AI Studio switches to a different chat
     * (new chat, history item, URL change). Each distinct chat is bound to its own project.
     */
    fun onAiStudioChatChanged(chatId: String) {
        val id = chatId.trim()
        if (id.isBlank() || id == activeChatId) return
        activeChatId = id

        viewModelScope.launch {
            val existing = repository.getProjectByChatId(id)
            if (existing != null) {
                if (_uiState.value.activeProject?.id != existing.id) {
                    resetRunStateForChatSwitch()
                    setActiveProject(existing)
                    showNotification("Switched to project \"${existing.name}\"")
                }
                return@launch
            }

            // Reuse the current project if it is still empty and unbound (e.g. the starter project)
            val active = _uiState.value.activeProject
            if (active != null && active.chatId.isBlank() && repository.getFilesSync(active.id).isEmpty()) {
                val bound = active.copy(chatId = id, updatedAt = System.currentTimeMillis())
                repository.updateProject(bound)
                _uiState.update { it.copy(activeProject = bound) }
                return@launch
            }

            val project = repository.createProject(DEFAULT_PROJECT_NAME, "ai_studio", id)
            resetRunStateForChatSwitch()
            setActiveProject(project)
            showNotification("New chat detected — started a new project")
        }
    }

    private fun resetRunStateForChatSwitch() {
        isProcessingRun = false
        lastProcessedRawResponse = null
        pendingUserPrompt = null
        _uiState.update {
            it.copy(
                currentResponseRun = null,
                responseRunsHistory = emptyList(),
                plannedOperations = emptyList(),
                pendingAmbiguousOps = emptyList(),
                isAmbiguityDialogOpen = false,
                hasAiResponseArrived = false,
                isAiProcessing = false,
                agentStatus = "Ready"
            )
        }
    }

    // ---------------------------------------------------------------------
    // Automatic project naming
    // ---------------------------------------------------------------------

    private val htmlTitleRegex = Regex("<title[^>]*>([\\s\\S]*?)</title>", RegexOption.IGNORE_CASE)

    private val nameFillerWords = setOf(
        "generate", "make", "create", "build", "write", "code", "coding", "develop", "design",
        "please", "can", "you", "me", "my", "a", "an", "the", "app", "apps", "application",
        "with", "and", "or", "for", "of", "to", "in", "on", "using", "use", "separate",
        "file", "files", "project", "simple", "basic", "nice", "small", "single", "page",
        "html", "css", "js", "javascript", "typescript", "react", "website", "web", "site",
        "responsive", "modern", "beautiful", "clean", "new", "some", "that", "this", "it"
    )

    /** Priority 1: the <title> of a generated HTML file. */
    private fun nameFromHtmlTitle(operations: List<PlannedOperationData>): String? {
        val htmlOp = operations.firstOrNull { it.path.substringAfterLast('.', "").lowercase() in setOf("html", "htm") }
            ?: return null
        val raw = htmlTitleRegex.find(htmlOp.content)?.groupValues?.getOrNull(1) ?: return null
        val clean = raw.replace(Regex("\\s+"), " ").trim()
        if (clean.isBlank() || clean.length > 60) return null
        if (clean.equals("document", ignoreCase = true) || clean.equals("untitled", ignoreCase = true)) return null
        return clean
    }

    /** Priority 2: a short title-cased name derived from the user's prompt. */
    private fun nameFromPrompt(prompt: String?): String? {
        if (prompt.isNullOrBlank()) return null
        val words = prompt
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() && it !in nameFillerWords && it.length > 1 }
        if (words.isEmpty()) return null
        return words.take(3).joinToString(" ") { w -> w.replaceFirstChar { it.uppercaseChar() } }
    }

    /**
     * Renames the project once a real app name can be detected, so the Artifacts zip and
     * every other project label stop saying "My Code Project".
     */
    private suspend fun maybeAutoRenameProject(
        projectId: String,
        operations: List<PlannedOperationData>,
        prompt: String?
    ) {
        val project = repository.getProjectById(projectId) ?: return
        if (!project.isNameAuto) return

        val detected = nameFromHtmlTitle(operations) ?: nameFromPrompt(prompt) ?: return
        if (detected.equals(project.name, ignoreCase = true)) return

        val renamed = repository.renameProject(projectId, detected) ?: return
        _uiState.update { state ->
            if (state.activeProject?.id == projectId) state.copy(activeProject = renamed) else state
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

    // Title of the single live item that mirrors AI Studio's streaming reply
    private val STREAMING_ACTIVITY_TITLE = "Generating in Google AI Studio..."

    // Last payload handed to processAiResponse, used to collapse streaming duplicates
    private var lastProcessedRawResponse: String? = null

    // True only while processAiResponse is actively narrating a run (not while a live placeholder waits)
    private var isProcessingRun = false

    // Prompt text captured from the AI Studio bridge before the input got cleared
    private var pendingUserPrompt: String? = null

    /**
     * Called from the WebView bridge the moment the user sends a prompt in AI Studio.
     * This is what STARTS the run: the Plan screen opens and the timer begins right here.
     */
    fun onUserPromptSent(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        val shown = if (clean.length > 200) clean.take(200).trimEnd() + "..." else clean
        pendingUserPrompt = shown

        val state = _uiState.value
        val current = state.currentResponseRun

        // A run is already live (e.g. duplicate bridge delivery): just make sure the prompt shows.
        if (isProcessingRun || (current != null && current.status == "running")) {
            if (current != null && current.status == "running" &&
                current.activities.none { it.type == AgentActivityType.PROMPT_SENT }
            ) {
                val promptItem = AgentActivityItem(
                    type = AgentActivityType.PROMPT_SENT,
                    title = "You: " + shown,
                    detailText = shown
                )
                val updated = current.copy(activities = listOf(promptItem) + current.activities)
                _uiState.update { it.copy(currentResponseRun = updated) }
            }
            return
        }

        val runNumber = state.responseRunsHistory.size + 1
        val liveRun = ResponseAgentRun(
            responseNumber = runNumber,
            startedAt = System.currentTimeMillis(),
            status = "running",
            summary = "Following AI Studio response #" + runNumber + "...",
            activities = listOf(
                AgentActivityItem(
                    type = AgentActivityType.PROMPT_SENT,
                    title = "You: " + shown,
                    detailText = shown
                )
            )
        )

        _uiState.update {
            it.copy(
                hasAiResponseArrived = true,
                isAiProcessing = true,
                agentStatus = "Generating",
                currentScreen = Screen.PLAN,
                currentResponseRun = liveRun
            )
        }
    }

    /**
     * Called from the WebView bridge on the false -> true generation edge.
     * The run already exists (started in onUserPromptSent); just narrate it. Idempotent.
     */
    fun onAiGenerationStarted() {
        if (isProcessingRun) return
        val state = _uiState.value
        val current = state.currentResponseRun

        val run = if (current != null && current.status == "running") {
            current
        } else {
            // Fallback: generation observed without a captured prompt — start the run now.
            ResponseAgentRun(
                responseNumber = state.responseRunsHistory.size + 1,
                startedAt = System.currentTimeMillis(),
                status = "running",
                summary = "Following AI Studio response #" + (state.responseRunsHistory.size + 1) + "..."
            )
        }

        val alreadyWatching = run.activities.any {
            it.type == AgentActivityType.THOUGHT && it.title == "Watching Google AI Studio..."
        }
        val updated = if (alreadyWatching) run else run.copy(
            activities = run.activities + AgentActivityItem(
                type = AgentActivityType.THOUGHT,
                title = "Watching Google AI Studio..."
            )
        )

        _uiState.update {
            it.copy(
                hasAiResponseArrived = true,
                isAiProcessing = true,
                agentStatus = "Generating",
                currentScreen = Screen.PLAN,
                currentResponseRun = updated
            )
        }
    }

    /**
     * Called repeatedly from the WebView bridge while AI Studio is streaming its reply.
     * Keeps a single live activity item in the Plan timeline updated with the partial text.
     */
    fun onAiStreamingUpdate(partial: String) {
        if (isProcessingRun) return
        val text = partial.trim()
        if (text.isBlank()) return

        val state = _uiState.value
        val run = state.currentResponseRun?.takeIf { it.status == "running" } ?: return

        // Render exactly what AI Studio streams — no truncation, no delay
        val existing = run.activities.indexOfFirst {
            it.type == AgentActivityType.THOUGHT && it.title == STREAMING_ACTIVITY_TITLE
        }
        val activities = if (existing >= 0) {
            run.activities.toMutableList().also { list ->
                list[existing] = list[existing].copy(detailText = text)
            }
        } else {
            // Pin the live streaming item at the top of the Timeline
            listOf(
                AgentActivityItem(
                    type = AgentActivityType.THOUGHT,
                    title = STREAMING_ACTIVITY_TITLE,
                    detailText = text,
                    isExpanded = true
                )
            ) + run.activities
        }

        _uiState.update {
            it.copy(
                agentStatus = "Generating",
                currentResponseRun = run.copy(activities = activities)
            )
        }
    }

    // PRIMARY PRODUCT FLOW: fully automatic processing of responses captured from the AI Studio WebView bridge
    fun processAiResponse(rawResponse: String, source: String = "ai_studio_bridge") {
        val activeProj = _uiState.value.activeProject ?: return
        if (rawResponse.isBlank()) {
            showNotification("No response content to analyze", isError = true)
            return
        }

        // Guard against duplicate bridge deliveries: the Plan timeline must open exactly once
        // per finished response and never restart mid-run.
        val activeRun = _uiState.value.currentResponseRun
        if (isProcessingRun) {
            lastProcessedRawResponse = rawResponse
            return
        }
        if (activeRun != null && activeRun.status != "running") {
            val recent = System.currentTimeMillis() - (activeRun.completedAt ?: activeRun.startedAt) < 5000
            if (recent && rawResponse == lastProcessedRawResponse) {
                lastProcessedRawResponse = rawResponse
                return
            }
        }
        lastProcessedRawResponse = rawResponse
        isProcessingRun = true

        viewModelScope.launch(Dispatchers.Default) {
            // Prompt text of this run, used as the naming fallback (cleared at the end of the run)
            val promptForNaming = pendingUserPrompt
            // Reuse the live placeholder run started by onAiGenerationStarted(), if any
            val livePlaceholder = _uiState.value.currentResponseRun?.takeIf { it.status == "running" }
            val agentRunId = livePlaceholder?.id ?: UUID.randomUUID().toString()
            val runNumber = livePlaceholder?.responseNumber ?: (_uiState.value.responseRunsHistory.size + 1)
            val startTime = livePlaceholder?.startedAt ?: System.currentTimeMillis()


            var run = livePlaceholder?.copy(
                summary = "Analyzing AI Studio response #$runNumber..."
            ) ?: ResponseAgentRun(
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
                isProcessingRun = false
                pendingUserPrompt = null
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

            // Step 4: Plan operations immediately so file items can stream live
            _uiState.update { it.copy(agentStatus = "Analysing") }
            val existingFiles = repository.getFilesSync(activeProj.id)
            val plannedOps = FileOperationPlanner.planOperations(candidates, existingFiles)
            _uiState.update { it.copy(plannedOperations = plannedOps) }

            val safeOps = plannedOps.filter { it.operation in listOf("CREATE", "UPDATE", "DELETE") }

            // Step 5: Stream each detected file candidate into the timeline in real time
            _uiState.update { it.copy(agentStatus = "Updating") }
            for (op in safeOps) {
                val isHtml = op.path.substringAfterLast('.', "").lowercase() in setOf("html", "htm")
                val actType = when {
                    isHtml -> AgentActivityType.HTML_FILE_CARD
                    op.operation == "CREATE" -> AgentActivityType.FILE_CREATE
                    else -> AgentActivityType.FILE_UPDATE
                }
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

            // Step 6: Inspect project structure
            appendActivity(
                AgentActivityItem(
                    type = AgentActivityType.ACTION,
                    title = "Inspect project structure safely"
                )
            )
            delay(150)

            // Step 7: Read existing relevant files
            for (f in existingFiles.take(3)) {
                appendActivity(
                    AgentActivityItem(
                        type = AgentActivityType.FILE_READ,
                        title = "Read",
                        targetPath = f.name
                    )
                )
                delay(100)
            }


            val summaryText = "AI Studio sync: ${safeOps.count { it.operation == "CREATE" }} created, ${safeOps.count { it.operation == "UPDATE" }} updated"
            val newVersion = repository.applyBatchOperations(
                projectId = activeProj.id,
                agentRunId = agentRunId,
                operations = safeOps,
                summary = summaryText,
                validationStatus = "validating"
            )

            // Derive a meaningful project name from this response (HTML <title>, else the prompt)
            maybeAutoRenameProject(activeProj.id, safeOps, promptForNaming)


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

            isProcessingRun = false
            pendingUserPrompt = null

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

    companion object {
        /** Placeholder used until a real app name can be detected from a response. */
        const val DEFAULT_PROJECT_NAME = "My Code Project"
    }
}
