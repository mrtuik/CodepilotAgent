package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureWhite)
            .testTag("artifacts_screen")
    ) {
        // Replit/Lovable-style Clean Header
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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

                // Contextual ZIP action (non-dominating)
                ShadcnButton(
                    text = "Export ZIP",
                    icon = Icons.Default.Download,
                    isPrimary = false,
                    onClick = onDownloadZip,
                    testTag = "artifact_export_zip_btn"
                )
            }
        }

        // Direct File Cards List (No generic bundle card)
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
                items(sortedFiles, key = { it.id }) { file ->
                    val change = uiState.changes.find { it.fileId == file.id }
                    val operation = change?.operation ?: "READY"
                    val isCreated = operation == "CREATE" || change == null
                    val statusLabel = if (isCreated) "Created" else "Updated"
                    val statusColor = if (isCreated) StatusReady else StatusActive
                    val statusBg = statusColor.copy(alpha = 0.08f)
                    val reasonText = change?.reason ?: "File added to workspace"

                    Surface(
                        color = PureWhite,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderLight, RoundedCornerShape(4.dp))
                            .testTag("artifact_file_${file.name}")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
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
                    }
                }
            }
        }
    }
}
