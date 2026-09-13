package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.repository.PlannedOperationData
import com.example.engine.RepairProposal
import com.example.gemini.GeminiModelMode
import com.example.ui.theme.*
import com.example.ui.viewmodel.PlanStageItem
import com.example.ui.viewmodel.Screen
import com.example.ui.viewmodel.StageStatus

@Composable
fun AppHeader(
    onOpenMenu: () -> Unit
) {
    Surface(
        color = PureWhite,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderLight)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Title: CodePilot only (no subtext)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Zinc950),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "CP",
                        color = PureWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "CodePilot",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Zinc950
                )
            }

            // Right action: simple menu icon
            IconButton(
                onClick = onOpenMenu,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menu",
                    tint = Zinc800,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun NavTab(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (isSelected) Zinc950 else Color.Transparent)
            .border(
                1.dp,
                if (isSelected) Zinc950 else Color.Transparent,
                RoundedCornerShape(4.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("nav_tab_$label")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) PureWhite else Zinc600,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) PureWhite else Zinc700
            )
        }
    }
}

@Composable
fun AgentStatusPill(status: String) {
    val (bgColor, textColor, dotColor) = when (status.lowercase()) {
        "ready" -> Triple(Zinc100, StatusReady, StatusReady)
        "planning", "analysing", "monitoring" -> Triple(Zinc100, StatusActive, StatusActive)
        "updating", "validating" -> Triple(Zinc100, StatusWarning, StatusWarning)
        "warning" -> Triple(Zinc100, StatusWarning, StatusWarning)
        "error" -> Triple(Zinc100, StatusError, StatusError)
        else -> Triple(Zinc100, Zinc700, Zinc500)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .border(1.dp, BorderLight, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = status,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FloatingAgentNav(
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    currentScreen: Screen,
    agentStatus: String,
    filesCount: Int,
    isProcessing: Boolean,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = PureWhite,
        shape = RoundedCornerShape(6.dp),
        shadowElevation = 0.dp,
        modifier = modifier
            .widthIn(max = 420.dp)
            .border(1.dp, BorderLight, RoundedCornerShape(6.dp))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main floating bar row: Expand/Collapse icon • AI Studio • Plan • Artifacts
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.height(42.dp)
            ) {
                // Expand / Collapse toggle (icon only)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isExpanded) Zinc200 else Color.Transparent)
                        .clickable { onToggleExpand() }
                        .testTag("floating_nav_expand_collapse"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = if (isExpanded) Zinc950 else Zinc700,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .height(18.dp)
                        .width(1.dp)
                        .background(BorderLight)
                )

                // AI Studio Workspace button
                FloatingNavButton(
                    label = "AI Studio",
                    icon = Icons.Default.Web,
                    isSelected = currentScreen == Screen.WORKSPACE,
                    onClick = { onNavigate(Screen.WORKSPACE) },
                    testTag = "floating_nav_workspace"
                )

                // Plan button
                FloatingNavButton(
                    label = "Plan",
                    icon = Icons.Default.Checklist,
                    isSelected = currentScreen == Screen.PLAN,
                    hasIndicator = isProcessing || agentStatus in listOf("Understanding", "Analysing", "Updating", "Validating"),
                    onClick = { onNavigate(Screen.PLAN) },
                    testTag = "floating_nav_plan"
                )

                // Artifacts button (shows file count badge)
                FloatingNavButton(
                    label = if (filesCount > 0) "Artifacts ($filesCount)" else "Artifacts",
                    icon = Icons.Default.Inventory2,
                    isSelected = currentScreen == Screen.ARTIFACTS || currentScreen == Screen.FILE_VIEWER || currentScreen == Screen.FILE_DIFF,
                    onClick = { onNavigate(Screen.ARTIFACTS) },
                    testTag = "floating_nav_artifacts"
                )
            }

            // Expanded tray with status & artifact indicator
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(BorderLight)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AgentStatusPill(status = agentStatus)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isProcessing) "Live Agent Active" else "Google AI Studio Ready",
                                fontSize = 11.sp,
                                color = Zinc600
                            )
                        }

                        // Artifact indicator: one small square per file created in the project
                        if (filesCount > 0) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .widthIn(max = 160.dp)
                                    .testTag("floating_artifact_squares")
                            ) {
                                repeat(filesCount) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(Zinc950)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FloatingNavButton(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    hasIndicator: Boolean = false,
    onClick: () -> Unit,
    testTag: String = ""
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (isSelected) Zinc950 else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag(testTag)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) PureWhite else Zinc700,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isSelected) PureWhite else Zinc800
            )
            if (hasIndicator) {
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(if (isSelected) PureWhite else Zinc500)
                )
            }
        }
    }
}

@Composable
fun AmbiguityResolutionDialog(
    isOpen: Boolean,
    pendingOperation: PlannedOperationData?,
    onDismiss: () -> Unit,
    onConfirm: (PlannedOperationData, String) -> Unit
) {
    if (!isOpen || pendingOperation == null) return

    var targetPath by remember(pendingOperation) {
        val suggested = if (pendingOperation.path.isNotBlank() && !pendingOperation.path.contains("untitled")) {
            pendingOperation.path
        } else {
            "src/component.js"
        }
        mutableStateOf(suggested)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = PureWhite,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .border(1.dp, BorderLight, RoundedCornerShape(8.dp))
                .padding(16.dp)
                .testTag("ambiguity_resolution_dialog")
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "File Destination Required",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Zinc950
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(StatusWarning.copy(alpha = 0.1f))
                            .border(1.dp, StatusWarning, RoundedCornerShape(3.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Uncertain Path",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = StatusWarning
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "AI Studio generated a code block without a confident file path. Specify the destination path in your project to create it safely.",
                    fontSize = 12.sp,
                    color = Zinc600
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Preview snippet
                val snippetLines = pendingOperation.content.lines().take(5).joinToString("\n")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Zinc100)
                        .border(1.dp, BorderLight, RoundedCornerShape(4.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = snippetLines,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Zinc800,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Target Path (relative to project root):",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Zinc700
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Zinc50)
                        .border(1.dp, BorderLight, RoundedCornerShape(4.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    BasicTextField(
                        value = targetPath,
                        onValueChange = { targetPath = it },
                        textStyle = TextStyle(
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Zinc950
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ShadcnButton(
                        text = "Skip",
                        isPrimary = false,
                        onClick = onDismiss
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    ShadcnButton(
                        text = "Create File",
                        isPrimary = true,
                        onClick = {
                            if (targetPath.isNotBlank()) {
                                onConfirm(pendingOperation, targetPath.trim())
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ShadcnButton(
    text: String,
    icon: ImageVector? = null,
    isPrimary: Boolean = false,
    isDestructive: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = ""
) {
    val bg = when {
        isDestructive -> StatusError
        isPrimary -> Zinc950
        else -> PureWhite
    }
    val contentColor = when {
        isDestructive || isPrimary -> PureWhite
        else -> Zinc900
    }
    val borderColor = when {
        isDestructive -> StatusError
        isPrimary -> Zinc950
        else -> BorderLight
    }

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = text,
                    tint = contentColor,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = contentColor
            )
        }
    }
}

@Composable
fun StageItemView(stage: PlanStageItem) {
    val statusColor = when (stage.status) {
        StageStatus.COMPLETED -> StatusReady
        StageStatus.IN_PROGRESS -> StatusActive
        StageStatus.WARNING -> StatusWarning
        StageStatus.FAILED -> StatusError
        StageStatus.PENDING -> Zinc400
    }

    val statusIcon = when (stage.status) {
        StageStatus.COMPLETED -> Icons.Default.Check
        StageStatus.IN_PROGRESS -> Icons.Default.HourglassTop
        StageStatus.WARNING -> Icons.Default.Warning
        StageStatus.FAILED -> Icons.Default.Close
        StageStatus.PENDING -> Icons.Default.RadioButtonUnchecked
    }

    Surface(
        color = PureWhite,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderLight, RoundedCornerShape(4.dp))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.1f))
                    .border(1.dp, statusColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${stage.stageNumber}. ${stage.title}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Zinc950
                    )
                    if (stage.timestamp.isNotBlank()) {
                        Text(
                            text = stage.timestamp,
                            fontSize = 10.sp,
                            color = Zinc400
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stage.explanation,
                    fontSize = 12.sp,
                    color = Zinc600,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
fun ToastNotificationBar(
    message: String,
    isError: Boolean,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (isError) Zinc950 else Zinc900)
                .border(1.dp, if (isError) StatusError else Zinc700, RoundedCornerShape(4.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isError) Icons.Default.ErrorOutline else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (isError) StatusError else PureWhite,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = message,
                        fontSize = 12.sp,
                        color = PureWhite
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = Zinc400,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun RepairDialog(
    isOpen: Boolean,
    proposals: List<RepairProposal>,
    onDismiss: () -> Unit,
    onApply: (List<RepairProposal>) -> Unit
) {
    if (!isOpen || proposals.isEmpty()) return

    val selected = remember(proposals) { mutableStateListOf<RepairProposal>().apply { addAll(proposals) } }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = PureWhite,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderLight, RoundedCornerShape(6.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Repair Proposal",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Zinc950
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Zinc500)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "The Repair Agent detected ${proposals.size} missing file reference(s) or broken paths in your project.",
                    fontSize = 12.sp,
                    color = Zinc600
                )
                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (prop in proposals) {
                        Surface(
                            color = Zinc50,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, BorderLight, RoundedCornerShape(4.dp))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = prop.targetPath,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = Zinc950
                                    )
                                    Text(
                                        text = prop.proposedAction,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = StatusActive
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = prop.problem,
                                    fontSize = 11.sp,
                                    color = StatusWarning
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = prop.reason,
                                    fontSize = 11.sp,
                                    color = Zinc500
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    ShadcnButton(
                        text = "Skip",
                        onClick = onDismiss,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    ShadcnButton(
                        text = "Apply Repairs",
                        isPrimary = true,
                        onClick = { onApply(selected) },
                        testTag = "apply_repairs_btn"
                    )
                }
            }
        }
    }
}

@Composable
fun NewProjectDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    if (!isOpen) return
    var name by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = PureWhite,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderLight, RoundedCornerShape(6.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "New Project",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Zinc950
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Enter a name for your local project workspace.",
                    fontSize = 12.sp,
                    color = Zinc600
                )
                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Zinc50)
                        .border(1.dp, BorderLight, RoundedCornerShape(4.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it },
                        textStyle = TextStyle(fontSize = 13.sp, color = Zinc950),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (name.isEmpty()) {
                        Text("e.g. Sketchware Proj, Web App", fontSize = 13.sp, color = Zinc400)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    ShadcnButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    ShadcnButton(
                        text = "Create",
                        isPrimary = true,
                        onClick = {
                            if (name.isNotBlank()) {
                                onCreate(name)
                                onDismiss()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ProjectMenuDialog(
    isOpen: Boolean,
    projectName: String,
    modelMode: GeminiModelMode,
    onDismiss: () -> Unit,
    onNewProject: () -> Unit,
    onExportZip: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleModelMode: () -> Unit
) {
    if (!isOpen) return

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = PureWhite,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 380.dp)
                .border(1.dp, BorderLight, RoundedCornerShape(6.dp))
                .testTag("project_menu_dialog")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "CodePilot Menu",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Zinc950
                        )
                        Text(
                            text = projectName,
                            fontSize = 11.sp,
                            color = Zinc500
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Zinc500,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BorderLight))
                Spacer(modifier = Modifier.height(8.dp))

                // Menu items
                MenuItemRow(
                    icon = Icons.Default.Add,
                    title = "New Project",
                    subtitle = "Create a new isolated local workspace",
                    onClick = {
                        onDismiss()
                        onNewProject()
                    }
                )

                MenuItemRow(
                    icon = Icons.Default.Download,
                    title = "Export Project ZIP",
                    subtitle = "Download all workspace files as an archive",
                    onClick = {
                        onDismiss()
                        onExportZip()
                    }
                )

                MenuItemRow(
                    icon = Icons.Default.Settings,
                    title = "Settings & API Keys",
                    subtitle = "Configure custom Gemini API key and preferences",
                    onClick = {
                        onDismiss()
                        onOpenSettings()
                    }
                )

                Spacer(modifier = Modifier.height(6.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BorderLight))
                Spacer(modifier = Modifier.height(8.dp))

                // Model mode toggle row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleModelMode() }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (modelMode == GeminiModelMode.HIGH_THINKING) Icons.Default.Psychology else Icons.Default.Bolt,
                            contentDescription = null,
                            tint = Zinc700,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Model: ${if (modelMode == GeminiModelMode.HIGH_THINKING) "High-Thinking" else "Low-Latency"}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Zinc950
                        )
                    }
                    Text(
                        text = "Switch",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Zinc600
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuItemRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        color = PureWhite,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Zinc800,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Zinc950
                )
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = Zinc500
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Zinc400,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
