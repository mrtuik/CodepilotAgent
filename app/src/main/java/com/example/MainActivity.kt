package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import com.example.data.repository.PlannedOperationData
import com.example.gemini.GeminiModelMode
import com.example.ui.components.*
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.PureWhite
import com.example.ui.viewmodel.CodePilotViewModel
import com.example.ui.viewmodel.Screen
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {

    private val viewModel: CodePilotViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            var isMenuOpen by remember { mutableStateOf(false) }

            // ZIP picker launcher
            val zipPickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri: Uri? ->
                if (uri != null) {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        viewModel.importZip(inputStream)
                    }
                }
            }

            fun exportZipFile() {
                try {
                    val bytes = viewModel.getExportZipBytes()
                    val fileName = "${uiState.activeProject?.name?.replace(" ", "_") ?: "project"}.zip"
                    val file = File(cacheDir, fileName)
                    FileOutputStream(file).use { it.write(bytes) }

                    val uri = FileProvider.getUriForFile(
                        this@MainActivity,
                        "${applicationContext.packageName}.fileprovider",
                        file
                    )
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(shareIntent, "Export Project Archive"))
                    viewModel.showNotification("ZIP archive prepared: $fileName")
                } catch (e: Exception) {
                    viewModel.showNotification("Export failed: ${e.message}", isError = true)
                }
            }

            // Android Hardware Back Handler
            BackHandler(enabled = uiState.currentScreen != Screen.WORKSPACE) {
                when (uiState.currentScreen) {
                    Screen.FILE_VIEWER, Screen.FILE_DIFF -> viewModel.navigate(Screen.ARTIFACTS)
                    else -> viewModel.navigate(Screen.WORKSPACE)
                }
            }

            val density = LocalDensity.current
            val imeBottom = WindowInsets.ime.getBottom(density)
            val isImeVisible = imeBottom > 0

            // Real measured height of the floating bottom nav (includes its own padding + nav bar inset)
            var floatingNavHeight by remember { mutableStateOf(0.dp) }

            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PureWhite),
                    topBar = {
                        AppHeader(
                            onOpenMenu = { isMenuOpen = true }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(PureWhite)
                    ) {
                        // AI Studio Workspace is rendered persistently so the WebView/DOM/session/scroll is NEVER destroyed or refreshed
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(0f)
                        ) {
                            AiStudioWorkspaceScreen(
                                uiState = uiState,
                                bottomClearance = if (isImeVisible) 0.dp else floatingNavHeight + 8.dp,
                                onDetectedResponseFromBridge = { capturedCode ->
                                    viewModel.processAiResponse(capturedCode, "ai_studio_bridge")
                                },
                                onPromptSentFromBridge = { promptText ->
                                    viewModel.onUserPromptSent(promptText)
                                },
                                onGenerationStartedFromBridge = {
                                    viewModel.onAiGenerationStarted()
                                },
                                onStreamingTextFromBridge = { partial ->
                                    viewModel.onAiStreamingUpdate(partial)
                                },
                                onChatChangedFromBridge = { chatId ->
                                    viewModel.onAiStudioChatChanged(chatId)
                                }

                            )
                        }

                        // Overlay screens (Plan, Artifacts, File Viewer, File Diff) rendered in an opaque layer on top
                        if (uiState.currentScreen != Screen.WORKSPACE) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(PureWhite)
                                    .zIndex(1f)
                            ) {
                                when (uiState.currentScreen) {
                                    Screen.WORKSPACE -> {
                                        // Handled by persistent layer
                                    }
                                    Screen.PLAN -> {
                                        PlanScreen(
                                            uiState = uiState,
                                            onBack = { viewModel.navigate(Screen.WORKSPACE) },
                                            onOpenArtifacts = { viewModel.navigate(Screen.ARTIFACTS) },
                                            onOpenFiles = { viewModel.navigate(Screen.ARTIFACTS) },
                                            onRunValidation = { /* Automated */ },
                                            onViewChanges = {
                                                val firstChange = uiState.changes.firstOrNull()
                                                if (firstChange != null) {
                                                    viewModel.viewDiff(firstChange)
                                                } else {
                                                    viewModel.navigate(Screen.ARTIFACTS)
                                                }
                                            }
                                        )
                                    }
                                    Screen.ARTIFACTS, Screen.FILES -> {
                                        ArtifactsScreen(
                                            uiState = uiState,
                                            onBack = { viewModel.navigate(Screen.WORKSPACE) },
                                            onViewFile = { viewModel.viewFile(it) },
                                            onViewDiffForFile = { viewModel.viewDiffForFile(it) },
                                            onDownloadZip = { exportZipFile() },
                                            onOpenFiles = { viewModel.navigate(Screen.ARTIFACTS) },
                                            onRestoreVersion = { viewModel.restoreVersion(it) },
                                            onRunValidation = { /* Automated */ },
                                            onShowNotification = { viewModel.showNotification(it) }
                                        )
                                    }
                                    Screen.FILE_VIEWER -> {
                                        FileViewerScreen(
                                            file = uiState.selectedFile,
                                            onBack = { viewModel.navigate(Screen.ARTIFACTS) },
                                            onViewDiff = { viewModel.viewDiffForFile(it) },
                                            onShowNotification = { viewModel.showNotification(it) }
                                        )
                                    }
                                    Screen.FILE_DIFF -> {
                                        FileDiffScreen(
                                            change = uiState.selectedChange,
                                            diffResult = uiState.selectedDiffResult,
                                            onBack = { viewModel.navigate(Screen.ARTIFACTS) },
                                            onViewFile = {
                                                val file = uiState.files.find { it.id == uiState.selectedChange?.fileId }
                                                if (file != null) {
                                                    viewModel.viewFile(file)
                                                } else {
                                                    viewModel.navigate(Screen.ARTIFACTS)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Compact floating bottom navigation: Expand/Collapse • AI Studio • Plan • Artifacts
                        if (!isImeVisible) {
                            FloatingAgentNav(
                                isExpanded = uiState.isBottomNavExpanded,
                                onToggleExpand = { viewModel.toggleBottomNavExpanded() },
                                currentScreen = uiState.currentScreen,
                                agentStatus = uiState.agentStatus,
                                filesCount = uiState.files.size,
                                isProcessing = uiState.isAiProcessing,
                                onNavigate = { viewModel.navigate(it) },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .onGloballyPositioned { coords ->
                                        val measured = with(density) { coords.size.height.toDp() }
                                        if (measured != floatingNavHeight) {
                                            floatingNavHeight = measured
                                        }
                                    }
                                    .navigationBarsPadding()
                                    .padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 4.dp)
                                    .zIndex(10f)
                            )
                        }

                        // Floating Toast / Notification
                        if (uiState.notification != null) {
                            ToastNotificationBar(
                                message = uiState.notification!!.message,
                                isError = uiState.notification!!.isError,
                                onDismiss = { viewModel.dismissNotification() }
                            )
                        }

                        // Ambiguity Resolution Dialog (Contextual prompt when AI creates uncertain file path)
                        AmbiguityResolutionDialog(
                            isOpen = uiState.isAmbiguityDialogOpen,
                            pendingOperation = uiState.pendingAmbiguousOps.firstOrNull(),
                            onDismiss = { viewModel.dismissAmbiguityDialog() },
                            onConfirm = { op, specifiedPath ->
                                viewModel.confirmAmbiguousOperation(op, specifiedPath)
                            }
                        )

                        // Dialogs
                        ProjectMenuDialog(
                            isOpen = isMenuOpen,
                            projectName = uiState.activeProject?.name ?: "Current Workspace",
                            modelMode = uiState.modelMode,
                            onDismiss = { isMenuOpen = false },
                            onNewProject = { viewModel.setNewProjectDialogOpen(true) },
                            onExportZip = { exportZipFile() },
                            onOpenSettings = { viewModel.setApiSettingsOpen(true) },
                            onToggleModelMode = {
                                val next = if (uiState.modelMode == GeminiModelMode.HIGH_THINKING) {
                                    GeminiModelMode.LOW_LATENCY
                                } else {
                                    GeminiModelMode.HIGH_THINKING
                                }
                                viewModel.setModelMode(next)
                            }
                        )

                        RepairDialog(
                            isOpen = uiState.isRepairModalOpen,
                            proposals = uiState.repairProposals,
                            onDismiss = { viewModel.setRepairModalOpen(false) },
                            onApply = { proposals ->
                                viewModel.applyRepairProposals(proposals)
                            }
                        )

                        NewProjectDialog(
                            isOpen = uiState.isNewProjectDialogOpen,
                            onDismiss = { viewModel.setNewProjectDialogOpen(false) },
                            onCreate = { projectName ->
                                viewModel.createProject(projectName)
                            }
                        )

                        SettingsDialog(
                            isOpen = uiState.isApiSettingsOpen,
                            currentKey = uiState.customApiKey,
                            currentMode = uiState.modelMode,
                            onDismiss = { viewModel.setApiSettingsOpen(false) },
                            onSaveKey = { viewModel.setCustomApiKey(it) },
                            onSetMode = { viewModel.setModelMode(it) }
                        )
                    }
                }
            }
        }
    }
}
