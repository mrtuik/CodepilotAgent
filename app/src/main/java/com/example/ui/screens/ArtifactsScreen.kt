package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.FileEntity
import com.example.ui.components.ShadcnButton
import com.example.ui.theme.*
import com.example.ui.viewmodel.CodePilotUiState

@Composable
fun ArtifactsScreen(
    uiState: CodePilotUiState,
    onBack: () -> Unit,
    onViewFile: (FileEntity) -> Unit,
    onViewDiffForFile: (FileEntity) -> Unit,
    onDownloadZip: () -> Unit,
    onOpenFiles: () -> Unit = {},
    onRestoreVersion: (String) -> Unit = {},
    onRunValidation: () -> Unit = {},
    onShowNotification: (String) -> Unit
) {
    val context = LocalContext.current

    // Latest changed or created files appear FIRST
    val sortedFiles = remember(uiState.files, uiState.changes) {
        uiState.files.sortedByDescending { file ->
            val lastChange = uiState.changes.find { it.fileId == file.id }
            lastChange?.createdAt ?: file.updatedAt
        }
    }

    // Zip bundle display name (matches MainActivity.exportZipFile() naming)
    val zipName = "${uiState.activeProject?.name?.replace(" ", "_") ?: "project"}.zip"
    val totalBytes = sortedFiles.sumOf { it.size }
    val totalSizeText = if (totalBytes > 1024) {
        "%.1f KB".format(totalBytes / 1024.0)
    } else {
        "$totalBytes bytes"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureWhite)
            .testTag("artifacts_screen")
    ) {
        // Clean Header: Back button + "Artifacts" title + file count subtitle
        Surface(
            color = PureWhite,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderLight)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Workspace",
                        tint = Zinc900,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = "Artifacts",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Zinc950
                    )
                    Text(
                        text = "${uiState.files.size} project file(s)",
                        fontSize = 11.sp,
                        color = Zinc500
                    )
                }
            }
        }

        // Body: empty state OR single collapsible zip bundle card
        if (sortedFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                        contentDescription = null,
                        tint = Zinc400,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "No Artifacts Yet",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Zinc900
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Files generated by Google AI Studio will appear here automatically.",
                        fontSize = 12.sp,
                        color = Zinc500,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    ZipBundleCard(
                        zipName = zipName,
                        filesCount = sortedFiles.size,
                        totalSizeText = totalSizeText,
                        files = sortedFiles,
                        changes = uiState.changes,
                        onDownloadZip = onDownloadZip,
                        onViewFile = onViewFile,
                        onViewDiffForFile = onViewDiffForFile,
                        onShowNotification = onShowNotification
                    )
                }
            }
        }
    }
}

@Composable
private fun ZipBundleCard(
    zipName: String,
    filesCount: Int,
    totalSizeText: String,
    files: List<FileEntity>,
    changes: List<com.example.data.db.ChangeEntity>,
    onDownloadZip: () -> Unit,
    onViewFile: (FileEntity) -> Unit,
    onViewDiffForFile: (FileEntity) -> Unit,
    onShowNotification: (String) -> Unit
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(true) }

    Surface(
        color = PureWhite,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderLight, RoundedCornerShape(6.dp))
            .testTag("artifact_zip_bundle_card")
    ) {
        Column {
            // Collapsed header row: zip icon, name, subtitle, chevron, Download button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Zinc100)
                            .border(1.dp, BorderLight, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Archive,
                            contentDescription = null,
                            tint = Zinc800,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = zipName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Zinc950
                        )
                        Text(
                            text = "$filesCount files • $totalSizeText",
                            fontSize = 11.sp,
                            color = Zinc500
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Expand / collapse chevron
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = Zinc600,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    // Download directly from the card
                    ShadcnButton(
                        text = "Download",
                        icon = Icons.Default.Download,
                        isPrimary = true,
                        onClick = onDownloadZip,
                        testTag = "artifact_download_zip_btn"
                    )
                }
            }

            // Expanded contents: nested per-file items with dividers
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    // Top divider
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(BorderLight)
                    )

                    files.forEachIndexed { index, file ->
                        val change = changes.find { it.fileId == file.id }
                        val operation = change?.operation ?: "READY"
                        val isCreated = operation == "CREATE" || change == null
                        val statusLabel = if (isCreated) "Created" else "Updated"
                        val statusColor = if (isCreated) StatusReady else StatusActive
                        val statusBg = statusColor.copy(alpha = 0.08f)
                        val reasonText = change?.reason ?: "File added to workspace"

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp)
                                .testTag("artifact_file_${file.name}")
                        ) {
                            // File header: Name, Status badge, Language
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                                        contentDescription = null,
                                        tint = Zinc700,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = file.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Zinc950
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    // Created / Updated status badge
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(statusBg)
                                            .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = statusLabel,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = statusColor
                                        )
                                    }
                                }

                                Text(
                                    text = file.language.uppercase(),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = Zinc400
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // File Path
                            Text(
                                text = file.path,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Zinc500
                            )

                            // Change Reason
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = reasonText,
                                fontSize = 11.sp,
                                color = Zinc600,
                                maxLines = 2
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Action Row: Open / Copy / Diff actions
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${file.size} bytes",
                                    fontSize = 10.sp,
                                    color = Zinc400
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Copy Action
                                    ShadcnButton(
                                        text = "Copy",
                                        icon = Icons.Default.ContentCopy,
                                        isPrimary = false,
                                        onClick = {
                                            val clip = ClipData.newPlainText(file.name, file.content)
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cm.setPrimaryClip(clip)
                                            onShowNotification("Copied ${file.name} to clipboard")
                                        },
                                        testTag = "copy_file_${file.name}"
                                    )

                                    // Diff Action
                                    ShadcnButton(
                                        text = "Diff",
                                        icon = Icons.Default.Difference,
                                        isPrimary = false,
                                        onClick = { onViewDiffForFile(file) },
                                        testTag = "diff_file_${file.name}"
                                    )

                                    // Open Action
                                    ShadcnButton(
                                        text = "Open",
                                        icon = Icons.Default.Visibility,
                                        isPrimary = true,
                                        onClick = { onViewFile(file) },
                                        testTag = "open_file_${file.name}"
                                    )
                                }
                            }
                        }

                        // Divider between nested entries (not after the last one)
                        if (index < files.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(BorderLight)
                            )
                        }
                    }
                }
            }
        }
    }
}
