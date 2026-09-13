package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.ChangeEntity
import com.example.engine.DiffLineType
import com.example.engine.DiffResult
import com.example.ui.components.ShadcnButton
import com.example.ui.theme.*

@Composable
fun FileDiffScreen(
    change: ChangeEntity?,
    diffResult: DiffResult?,
    onBack: () -> Unit,
    onViewFile: () -> Unit
) {
    if (change == null || diffResult == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No diff data available", color = Zinc500)
        }
        return
    }

    val opColor = when (change.operation) {
        "CREATE" -> StatusReady
        "UPDATE" -> StatusActive
        "DELETE" -> StatusError
        "REPAIR" -> StatusWarning
        else -> Zinc700
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureWhite)
            .testTag("file_diff_screen")
    ) {
        // Header
        Surface(
            color = PureWhite,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderLight)
        ) {
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = change.path.substringAfterLast('/'),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Zinc950
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(opColor.copy(alpha = 0.1f))
                                    .border(1.dp, opColor, RoundedCornerShape(3.dp))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = change.operation,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = opColor
                                )
                            }
                        }
                        Text(
                            text = change.path,
                            fontSize = 11.sp,
                            color = Zinc500,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                ShadcnButton(
                    text = "View File",
                    icon = Icons.Default.Visibility,
                    onClick = onViewFile
                )
            }
        }

        // Change Reason Callout Card
        Surface(
            color = Zinc50,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderLight)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Change Reason",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Zinc600
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = change.reason,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Zinc950
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "+${diffResult.addedCount} lines",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = StatusReady,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "-${diffResult.removedCount} lines",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = StatusError,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Unified Diff Viewer
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(PureWhite)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(rememberScrollState())
            ) {
                items(diffResult.lines) { line ->
                    val lineBg = when (line.type) {
                        DiffLineType.ADDED -> DiffAddBg
                        DiffLineType.REMOVED -> DiffRemoveBg
                        DiffLineType.UNCHANGED -> PureWhite
                    }
                    val textColor = when (line.type) {
                        DiffLineType.ADDED -> DiffAddText
                        DiffLineType.REMOVED -> DiffRemoveText
                        DiffLineType.UNCHANGED -> Zinc800
                    }
                    val symbol = when (line.type) {
                        DiffLineType.ADDED -> "+"
                        DiffLineType.REMOVED -> "-"
                        DiffLineType.UNCHANGED -> " "
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(lineBg)
                            .padding(vertical = 2.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Line number
                        Text(
                            text = "${line.oldLineNum ?: ""} ${line.newLineNum ?: ""}".trim().ifBlank { " " },
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Zinc400,
                            modifier = Modifier.width(44.dp)
                        )
                        // Symbol
                        Text(
                            text = symbol,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = textColor,
                            modifier = Modifier.width(16.dp)
                        )
                        // Line Text
                        Text(
                            text = line.text,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = textColor
                        )
                    }
                }
            }
        }
    }
}
