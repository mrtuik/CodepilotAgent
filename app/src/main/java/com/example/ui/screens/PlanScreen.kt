package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.PlannedOperationData
import com.example.ui.theme.*
import com.example.ui.viewmodel.AgentActivityItem
import com.example.ui.viewmodel.AgentActivityType
import com.example.ui.viewmodel.CodePilotUiState
import com.example.ui.viewmodel.ResponseAgentRun

@Composable
fun PlanScreen(
    uiState: CodePilotUiState,
    onBack: () -> Unit,
    onOpenArtifacts: () -> Unit,
    onOpenFiles: () -> Unit = onOpenArtifacts,
    onRunValidation: () -> Unit = {},
    onViewChanges: () -> Unit = onOpenArtifacts
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Timeline, 1: Changes
    val currentRun: ResponseAgentRun? = uiState.currentResponseRun

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureWhite)
            .testTag("plan_screen")
    ) {
        // Top Header: Back Arrow • Segmented Control (Timeline / Changes) • Artifacts shortcut
        Surface(
            color = PureWhite,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderLight)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Back button
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(34.dp).testTag("plan_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to AI Studio",
                            tint = Zinc900,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Lovable-style Segmented Switch: Timeline | Changes
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Zinc100)
                            .padding(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SegmentedTabItem(
                            title = "Timeline",
                            isSelected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            testTag = "plan_tab_timeline"
                        )
                        SegmentedTabItem(
                            title = "Changes",
                            badgeCount = currentRun?.changedOperations?.size ?: 0,
                            isSelected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            testTag = "plan_tab_changes"
                        )
                    }

                    // Right action: Quick link to Artifacts
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Zinc100)
                            .border(1.dp, BorderLight, RoundedCornerShape(4.dp))
                            .clickable { onOpenArtifacts() }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                            .testTag("plan_artifacts_chip")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Inventory2,
                                contentDescription = "Artifacts",
                                tint = Zinc800,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Artifacts",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Zinc800
                            )
                        }
                    }
                }

                // Subtitle metadata: Worked for Xs • N actions executed
                if (currentRun != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = Zinc500,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (currentRun.status == "running") "Working on response #${currentRun.responseNumber}..."
                            else "Worked for ${currentRun.elapsedSeconds}s",
                            fontSize = 11.sp,
                            color = Zinc600
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "•",
                            fontSize = 11.sp,
                            color = Zinc400
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "${currentRun.activities.size} actions executed",
                            fontSize = 11.sp,
                            color = Zinc600
                        )
                        if (currentRun.status == "running") {
                            Spacer(modifier = Modifier.width(8.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(10.dp),
                                strokeWidth = 1.5.dp,
                                color = Zinc900
                            )
                        }
                    }
                }
            }
        }

        // Main Content Area
        if (currentRun == null) {
            // Response-based requirement: Clean empty state before any AI Studio response
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Zinc100)
                            .border(1.dp, BorderLight, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Checklist,
                            contentDescription = null,
                            tint = Zinc600,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "No Agent Activity Yet",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Zinc950
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Responses from Google AI Studio will automatically detect code and trigger live agent timelines here.",
                        fontSize = 12.sp,
                        color = Zinc500,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.widthIn(max = 280.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Zinc900)
                            .clickable { onBack() }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .testTag("plan_return_workspace_btn")
                    ) {
                        Text(
                            text = "Open AI Studio",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = PureWhite
                        )
                    }
                }
            }
        } else if (selectedTab == 0) {
            // Timeline View (Lovable-style vertical stream)
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 14.dp, bottom = 80.dp)
            ) {
                items(currentRun.activities, key = { it.id }) { item ->
                    LovableTimelineNode(item = item)
                }
            }
        } else {
            // Changes View (List of file modifications in this response)
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (currentRun.changedOperations.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No file mutations were performed in this response.",
                                fontSize = 12.sp,
                                color = Zinc500
                            )
                        }
                    }
                } else {
                    items(currentRun.changedOperations) { op ->
                        ChangedOperationCard(op = op, onOpenArtifacts = onOpenArtifacts)
                    }
                }
            }
        }
    }
}

@Composable
private fun SegmentedTabItem(
    title: String,
    badgeCount: Int = 0,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (isSelected) PureWhite else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) Zinc950 else Zinc600
            )
            if (badgeCount > 0) {
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isSelected) Zinc200 else Zinc300)
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = badgeCount.toString(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Zinc800
                    )
                }
            }
        }
    }
}

@Composable
private fun LovableTimelineNode(item: AgentActivityItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Left timeline track (icon node + connecting line)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(24.dp)
        ) {
            when (item.type) {
                AgentActivityType.THOUGHT -> {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(15.dp)
                    )
                }
                AgentActivityType.SUMMARY_CARD -> {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Zinc400)
                    )
                }
                AgentActivityType.ACTION -> {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = Zinc700,
                        modifier = Modifier.size(14.dp)
                    )
                }
                AgentActivityType.FILE_READ -> {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                        contentDescription = null,
                        tint = Zinc500,
                        modifier = Modifier.size(14.dp)
                    )
                }
                AgentActivityType.FILE_CREATE -> {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.NoteAdd,
                        contentDescription = null,
                        tint = StatusReady,
                        modifier = Modifier.size(14.dp)
                    )
                }
                AgentActivityType.FILE_UPDATE -> {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = StatusActive,
                        modifier = Modifier.size(13.dp)
                    )
                }
                AgentActivityType.VALIDATE -> {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = null,
                        tint = StatusReady,
                        modifier = Modifier.size(14.dp)
                    )
                }
                AgentActivityType.ARTIFACT_READY -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = StatusReady,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Subtle vertical connecting track
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(24.dp)
                    .background(BorderLight)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Content area
        Column(modifier = Modifier.weight(1f)) {
            when (item.type) {
                AgentActivityType.THOUGHT -> {
                    Text(
                        text = item.title,
                        fontSize = 12.sp,
                        color = Zinc600,
                        fontWeight = FontWeight.Medium
                    )
                }
                AgentActivityType.SUMMARY_CARD -> {
                    Surface(
                        color = PureWhite,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderLight, RoundedCornerShape(6.dp))
                    ) {
                        Text(
                            text = item.detailText ?: item.title,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = Zinc950,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                AgentActivityType.ACTION -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = item.title,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Zinc800
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Zinc400,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                AgentActivityType.FILE_READ, AgentActivityType.FILE_CREATE, AgentActivityType.FILE_UPDATE -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${item.title} ",
                            fontSize = 12.sp,
                            color = Zinc600
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    when (item.type) {
                                        AgentActivityType.FILE_CREATE -> Color(0xFFF0FDF4)
                                        AgentActivityType.FILE_UPDATE -> Color(0xFFEFF6FF)
                                        else -> Zinc100
                                    }
                                )
                                .border(
                                    1.dp,
                                    when (item.type) {
                                        AgentActivityType.FILE_CREATE -> Color(0xFFBBF7D0)
                                        AgentActivityType.FILE_UPDATE -> Color(0xFFBFDBFE)
                                        else -> BorderLight
                                    },
                                    RoundedCornerShape(3.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = item.targetPath ?: "",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                                color = when (item.type) {
                                    AgentActivityType.FILE_CREATE -> Color(0xFF166534)
                                    AgentActivityType.FILE_UPDATE -> Color(0xFF1E40AF)
                                    else -> Zinc900
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                AgentActivityType.VALIDATE -> {
                    Text(
                        text = item.title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Zinc800
                    )
                }
                AgentActivityType.ARTIFACT_READY -> {
                    Text(
                        text = item.title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = StatusReady
                    )
                }
            }
        }
    }
}

@Composable
private fun ChangedOperationCard(
    op: PlannedOperationData,
    onOpenArtifacts: () -> Unit
) {
    val isCreate = op.operation == "CREATE"
    Surface(
        color = PureWhite,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderLight, RoundedCornerShape(4.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (isCreate) Icons.AutoMirrored.Filled.NoteAdd else Icons.Default.Edit,
                    contentDescription = null,
                    tint = if (isCreate) StatusReady else StatusActive,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = op.path,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = Zinc950,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = op.reason,
                        fontSize = 11.sp,
                        color = Zinc500
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (isCreate) Color(0xFFECFDF5) else Color(0xFFEFF6FF))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (isCreate) "+ CREATE" else "~ UPDATE",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (isCreate) Color(0xFF047857) else Color(0xFF1D4ED8)
                )
            }
        }
    }
}
