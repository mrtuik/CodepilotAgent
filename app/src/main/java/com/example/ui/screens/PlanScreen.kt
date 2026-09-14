package com.example.ui.screens

import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.PlannedOperationData
import kotlinx.coroutines.delay
import com.example.ui.theme.*
import com.example.ui.viewmodel.AgentActivityItem
import com.example.ui.viewmodel.AgentActivityType
import com.example.ui.viewmodel.CodePilotUiState
import com.example.ui.viewmodel.FileCardState
import com.example.ui.viewmodel.ResponseAgentRun
import com.example.ui.viewmodel.TimelineStreamSegment

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
                    LovableTimelineNode(
                        item = item,
                        runStartedAt = currentRun.startedAt,
                        isRunning = currentRun.status == "running",
                        runElapsedSeconds = currentRun.elapsedSeconds,
                        onOpenFileFlow = onOpenFiles
                    )
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
private fun LovableTimelineNode(
    item: AgentActivityItem,
    runStartedAt: Long = item.timestamp,
    isRunning: Boolean = false,
    runElapsedSeconds: Int = 0,
    onOpenFileFlow: () -> Unit = {}
) {
    // New timeline text/event: fade + translateY(4px -> 0), ~140ms ease-out. Plays once per
    // node (each node is a distinct, key()-ed composable instance in the LazyColumn above).
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }
    val entranceAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
        label = "timelineNodeAlpha"
    )
    val entranceOffset by animateDpAsState(
        targetValue = if (isVisible) 0.dp else 4.dp,
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
        label = "timelineNodeOffset"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .offset(y = entranceOffset)
            .alpha(entranceAlpha),
        verticalAlignment = Alignment.Top
    ) {
        // Left timeline track (icon node + connecting line)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(24.dp)
        ) {
            when (item.type) {
                AgentActivityType.PROMPT_SENT -> {
                    Spacer(modifier = Modifier.size(0.dp))
                }
                AgentActivityType.THOUGHT -> {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = Zinc500,
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
                AgentActivityType.HTML_FILE_CARD -> {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = Zinc600,
                        modifier = Modifier.size(14.dp)
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
                AgentActivityType.STREAM_PREVIEW -> {
                    // No monitoring label or icon here by design — the live text and file
                    // cards rendered alongside this node are the only "processing" signal.
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Zinc300)
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
                AgentActivityType.PROMPT_SENT -> {
                    Text(
                        text = item.title.removePrefix("You: ").removePrefix("You:").trim(),
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.Medium,
                        color = Zinc900,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
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
                                        AgentActivityType.FILE_CREATE -> Zinc100
                                        AgentActivityType.FILE_UPDATE -> Zinc100
                                        else -> Zinc100
                                    }
                                )
                                .border(
                                    1.dp,
                                    when (item.type) {
                                        AgentActivityType.FILE_CREATE -> BorderLight
                                        AgentActivityType.FILE_UPDATE -> BorderLight
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
                                    AgentActivityType.FILE_CREATE -> Zinc900
                                    AgentActivityType.FILE_UPDATE -> Zinc900
                                    else -> Zinc900
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                AgentActivityType.HTML_FILE_CARD -> {
                    val seconds = rememberLiveElapsedSeconds(runStartedAt, isRunning, runElapsedSeconds)
                    Surface(
                        color = PureWhite,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderLight, RoundedCornerShape(6.dp))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${item.title} HTML",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Zinc900
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                if (isRunning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(10.dp),
                                        strokeWidth = 1.5.dp,
                                        color = Zinc600
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = Zinc500,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "${seconds}s",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Zinc600
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = item.targetPath ?: "",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                                color = Zinc900,
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
                AgentActivityType.STREAM_PREVIEW -> {
                    // Live reply preview: explanatory text renders live as it arrives; any code
                    // is reduced to a compact file-operation card — raw source is never shown.
                    StreamPreviewContent(
                        segments = item.streamSegments,
                        onOpenFileFlow = onOpenFileFlow
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
                    .background(if (isCreate) Zinc100 else Zinc100)
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (isCreate) "+ CREATE" else "~ UPDATE",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (isCreate) Zinc900 else Zinc900
                )
            }
        }
    }
}

/** Live ticking elapsed seconds for the current run; freezes at the final value once the run ends. */
@Composable
private fun rememberLiveElapsedSeconds(startedAt: Long, isRunning: Boolean, fallbackSeconds: Int): Int {
    var now by remember(startedAt) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt, isRunning) {
        while (isRunning) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    return if (isRunning) (((now - startedAt) / 1000).toInt()).coerceAtLeast(0) else fallbackSeconds
}

// ---------------------------------------------------------------------------
// Live streaming rendering (Timeline)
//
// The ViewModel has already classified the in-progress AI Studio reply into
// TimelineStreamSegment.Prose / .FileCard (see CodePilotViewModel.kt) — no
// raw source text ever reaches this file. Rendering only ever sees metadata:
// explanatory strings meant to be shown verbatim, and file-card fields
// (name, language, line count, state) with no code content attached.
// ---------------------------------------------------------------------------

/** Renders the live reply preview: prose as chat bubbles, code as file-operation cards. */
@Composable
private fun StreamPreviewContent(
    segments: List<TimelineStreamSegment>,
    onOpenFileFlow: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        segments.forEachIndexed { index, segment ->
            // Keyed by position: a segment's entrance animation plays once when it first
            // appears at this slot, and keeps playing as its own content is refreshed by
            // later streaming updates (e.g. a card's line count ticking up in place).
            key(index) {
                FadeSlideInAppear {
                    when (segment) {
                        is TimelineStreamSegment.Prose -> StreamingChatBubble(segment.text)
                        is TimelineStreamSegment.FileCard -> LiveFileOperationCard(
                            segment = segment,
                            onOpenFileFlow = onOpenFileFlow
                        )
                    }
                }
            }
        }
    }
}

/**
 * Wraps a single new timeline element (a chat bubble or a file card) in its entrance animation:
 * opacity 0 -> 1 and translateY(4px -> 0), ~160ms ease-out. Plays exactly once, the moment this
 * composable is first placed at its slot.
 */
@Composable
private fun FadeSlideInAppear(content: @Composable () -> Unit) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "streamItemAlpha"
    )
    val offsetY by animateDpAsState(
        targetValue = if (isVisible) 0.dp else 4.dp,
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "streamItemOffset"
    )
    Box(modifier = Modifier.offset(y = offsetY).alpha(alpha)) {
        content()
    }
}

/** Plain explanatory text, styled as a monochrome black-outline chat bubble. */
@Composable
private fun StreamingChatBubble(text: String) {
    Surface(
        color = PureWhite,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Zinc900, RoundedCornerShape(10.dp))
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = Zinc900,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        )
    }
}

/** Human-facing label for a [FileCardState]. Never a checkmark or success graphic — "Ready" is
 *  communicated purely through this label and the card's animation stopping. */
private fun FileCardState.label(): String = when (this) {
    FileCardState.CREATING -> "Creating"
    FileCardState.WRITING -> "Writing"
    FileCardState.UPDATING -> "Updating"
    FileCardState.ANALYSING -> "Analysing"
    FileCardState.VALIDATING -> "Validating"
    FileCardState.READY -> "Ready"
    FileCardState.ERROR -> "Error"
}

/**
 * A detected code block rendered as a compact card — never the underlying source. Pulses subtly
 * while its file is still being written; becomes fully static once Ready (or Error). Tapping it
 * opens the app's existing file viewer/diff flow rather than any new in-Plan code preview.
 */
@Composable
private fun LiveFileOperationCard(
    segment: TimelineStreamSegment.FileCard,
    onOpenFileFlow: () -> Unit
) {
    val isActive = segment.state != FileCardState.READY && segment.state != FileCardState.ERROR
    val pulseAlpha = if (isActive) {
        val transition = rememberInfiniteTransition(label = "fileCardPulse")
        transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 700),
                repeatMode = RepeatMode.Reverse
            ),
            label = "fileCardPulseAlpha"
        ).value
    } else {
        1f
    }

    val displayName = segment.fileName ?: "Writing code..."
    val stateColor = when (segment.state) {
        FileCardState.ERROR -> StatusError
        FileCardState.READY -> Zinc500
        else -> Zinc900.copy(alpha = pulseAlpha)
    }

    Surface(
        color = PureWhite,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderLight, RoundedCornerShape(8.dp))
            .clickable(onClick = onOpenFileFlow)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(Zinc100)
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = segment.language,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Zinc700.copy(alpha = if (isActive) pulseAlpha else 1f)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = displayName,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = Zinc950,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (segment.lineCount > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${segment.lineCount}L",
                            fontSize = 10.sp,
                            color = Zinc500
                        )
                    }
                }
                Text(
                    text = segment.state.label(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = stateColor
                )
            }
        }
    }
}
