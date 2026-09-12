package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.FileEntity
import com.example.ui.components.ShadcnButton
import com.example.ui.theme.*

@Composable
fun FileViewerScreen(
    file: FileEntity?,
    onBack: () -> Unit,
    onViewDiff: (FileEntity) -> Unit,
    onShowNotification: (String) -> Unit
) {
    val context = LocalContext.current
    if (file == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No file selected", color = Zinc500)
        }
        return
    }

    val lines = file.content.lines()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureWhite)
            .testTag("file_viewer_screen")
    ) {
        // Top Header
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
                        Text(
                            text = file.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Zinc950
                        )
                        Text(
                            text = file.path,
                            fontSize = 11.sp,
                            color = Zinc500,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    ShadcnButton(
                        text = "Diff",
                        icon = Icons.Default.Difference,
                        onClick = { onViewDiff(file) },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    ShadcnButton(
                        text = "Copy",
                        icon = Icons.Default.ContentCopy,
                        isPrimary = true,
                        onClick = {
                            val clip = ClipData.newPlainText(file.name, file.content)
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(clip)
                            onShowNotification("Copied ${file.name} to clipboard")
                        }
                    )
                }
            }
        }

        // File Metadata Bar
        Surface(
            color = Zinc50,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderLight)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${lines.size} lines • ${file.size} bytes",
                    fontSize = 11.sp,
                    color = Zinc600,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = file.language.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Zinc700
                )
            }
        }

        // Code Area (Monospace, line numbers, horizontal scroll)
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
                itemsIndexed(lines) { index, line ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp)
                    ) {
                        // Line number column
                        Text(
                            text = "${index + 1}",
                            modifier = Modifier
                                .width(42.dp)
                                .padding(end = 8.dp),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Zinc400,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                        )
                        // Code line
                        Text(
                            text = line,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Zinc950
                        )
                    }
                }
            }
        }
    }
}
