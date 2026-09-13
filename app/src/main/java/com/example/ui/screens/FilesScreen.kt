package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.db.FileEntity
import com.example.ui.components.AgentStatusPill
import com.example.ui.components.ShadcnButton
import com.example.ui.theme.*
import com.example.ui.viewmodel.CodePilotUiState

@Composable
fun FilesScreen(
    uiState: CodePilotUiState,
    onBack: () -> Unit,
    onViewFile: (FileEntity) -> Unit,
    onViewDiff: (FileEntity) -> Unit,
    onDeleteFile: (String) -> Unit,
    onAddOrUpdateFile: (String, String, String) -> Unit,
    onSearchChanged: (String) -> Unit,
    onFilterChanged: (String) -> Unit,
    onImportZip: () -> Unit,
    onExportZip: () -> Unit,
    onRepair: () -> Unit,
    onValidate: () -> Unit = {}
) {
    var isAddFileDialogOpen by remember { mutableStateOf(false) }
    var fileToDelete by remember { mutableStateOf<FileEntity?>(null) }

    val filterChips = listOf("All", "Created", "Updated", "Deleted", "Warning")

    // Filter files
    val filteredFiles = remember(uiState.files, uiState.searchQuery, uiState.activeFileFilter, uiState.changes) {
        uiState.files.filter { file ->
            val matchesQuery = uiState.searchQuery.isBlank() ||
                    file.name.contains(uiState.searchQuery, ignoreCase = true) ||
                    file.path.contains(uiState.searchQuery, ignoreCase = true)

            val matchesFilter = when (uiState.activeFileFilter) {
                "Created" -> uiState.changes.any { it.fileId == file.id && it.operation == "CREATE" }
                "Updated" -> uiState.changes.any { it.fileId == file.id && it.operation == "UPDATE" }
                "Warning" -> uiState.validationResult?.warnings?.any { it.contains(file.name) } == true
                else -> true
            }

            matchesQuery && matchesFilter
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureWhite)
            .testTag("files_screen")
    ) {
        // Header
        Surface(
            color = PureWhite,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderLight)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Zinc900)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = "Project Files",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Zinc950
                            )
                            Text(
                                text = "${uiState.files.size} file(s) in workspace",
                                fontSize = 11.sp,
                                color = Zinc500
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ShadcnButton(
                            text = "New File",
                            icon = Icons.Default.Add,
                            isPrimary = true,
                            onClick = { isAddFileDialogOpen = true },
                            testTag = "add_file_btn"
                        )
                    }
                }

                // Search Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Zinc50)
                        .border(1.dp, BorderLight, RoundedCornerShape(4.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = Zinc400, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicTextField(
                            value = uiState.searchQuery,
                            onValueChange = onSearchChanged,
                            textStyle = TextStyle(fontSize = 12.sp, color = Zinc950),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (uiState.searchQuery.isEmpty()) {
                        Row(modifier = Modifier.padding(start = 24.dp)) {
                            Text("Search filename or relative path...", fontSize = 12.sp, color = Zinc400)
                        }
                    }
                }

                // Filter Chips & Project Controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (chip in filterChips) {
                        val isSelected = uiState.activeFileFilter == chip
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (isSelected) Zinc950 else Zinc100)
                                .border(1.dp, if (isSelected) Zinc950 else BorderLight, RoundedCornerShape(3.dp))
                                .clickable { onFilterChanged(chip) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = chip,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) PureWhite else Zinc700
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))
                    Box(modifier = Modifier.height(18.dp).width(1.dp).background(BorderLight))
                    Spacer(modifier = Modifier.width(4.dp))

                    ShadcnButton(text = "Import ZIP", icon = Icons.Default.Upload, onClick = onImportZip)
                    ShadcnButton(text = "Export ZIP", icon = Icons.Default.Download, onClick = onExportZip)
                    ShadcnButton(text = "Repair", icon = Icons.Default.Build, onClick = onRepair)
                }
            }
        }

        // File List / Tree
        if (filteredFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Zinc300, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (uiState.files.isEmpty()) "No files in project yet." else "No files matching filter.",
                        fontSize = 13.sp,
                        color = Zinc500
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ShadcnButton(
                        text = "Create First File",
                        icon = Icons.Default.Add,
                        isPrimary = true,
                        onClick = { isAddFileDialogOpen = true }
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredFiles) { file ->
                    val change = uiState.changes.find { it.fileId == file.id }
                    val op = change?.operation ?: "SYNCED"

                    Surface(
                        color = PureWhite,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderLight, RoundedCornerShape(4.dp))
                            .clickable { onViewFile(file) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = when (file.language) {
                                        "html" -> Icons.Default.Code
                                        "css" -> Icons.Default.Style
                                        "javascript", "typescript" -> Icons.Default.Javascript
                                        "json" -> Icons.Default.DataObject
                                        else -> Icons.AutoMirrored.Filled.InsertDriveFile
                                    },
                                    contentDescription = null,
                                    tint = Zinc700,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = file.name,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Zinc950
                                    )
                                    Text(
                                        text = file.path,
                                        fontSize = 10.sp,
                                        color = Zinc500,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${file.size} B",
                                    fontSize = 10.sp,
                                    color = Zinc400,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                IconButton(
                                    onClick = { onViewDiff(file) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Difference, contentDescription = "Diff", tint = Zinc600, modifier = Modifier.size(16.dp))
                                }
                                IconButton(
                                    onClick = { fileToDelete = file },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = StatusError, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(72.dp))
                }
            }
        }
    }

    // Add File Dialog
    if (isAddFileDialogOpen) {
        var filePath by remember { mutableStateOf("") }
        var fileContent by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { isAddFileDialogOpen = false }) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = PureWhite,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderLight, RoundedCornerShape(6.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Add New File", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Zinc950)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Relative Path (e.g. index.html, src/app.js)", fontSize = 11.sp, color = Zinc500)
                    Spacer(modifier = Modifier.height(4.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(Zinc50)
                            .border(1.dp, BorderLight, RoundedCornerShape(4.dp))
                            .padding(8.dp)
                    ) {
                        BasicTextField(
                            value = filePath,
                            onValueChange = { filePath = it },
                            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Zinc950),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (filePath.isEmpty()) {
                            Text("src/index.html", fontSize = 12.sp, color = Zinc400, fontFamily = FontFamily.Monospace)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("File Content", fontSize = 11.sp, color = Zinc500)
                    Spacer(modifier = Modifier.height(4.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Zinc50)
                            .border(1.dp, BorderLight, RoundedCornerShape(4.dp))
                            .padding(8.dp)
                    ) {
                        BasicTextField(
                            value = fileContent,
                            onValueChange = { fileContent = it },
                            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Zinc950),
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        ShadcnButton(text = "Cancel", onClick = { isAddFileDialogOpen = false }, modifier = Modifier.padding(end = 8.dp))
                        ShadcnButton(
                            text = "Save File",
                            isPrimary = true,
                            onClick = {
                                if (filePath.isNotBlank()) {
                                    onAddOrUpdateFile(filePath, fileContent, "User created file manually")
                                    isAddFileDialogOpen = false
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (fileToDelete != null) {
        val target = fileToDelete!!
        Dialog(onDismissRequest = { fileToDelete = null }) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = PureWhite,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderLight, RoundedCornerShape(6.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Delete File", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Zinc950)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Are you sure you want to delete '${target.path}'? A new version snapshot will record this deletion.",
                        fontSize = 12.sp,
                        color = Zinc600
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        ShadcnButton(text = "Cancel", onClick = { fileToDelete = null }, modifier = Modifier.padding(end = 8.dp))
                        ShadcnButton(
                            text = "Delete",
                            isDestructive = true,
                            onClick = {
                                onDeleteFile(target.id)
                                fileToDelete = null
                            }
                        )
                    }
                }
            }
        }
    }
}
