package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import com.example.gemini.GeminiModelMode
import com.example.ui.theme.*

@Composable
fun SettingsDialog(
    isOpen: Boolean,
    currentKey: String,
    currentMode: GeminiModelMode,
    onDismiss: () -> Unit,
    onSaveKey: (String) -> Unit,
    onSetMode: (GeminiModelMode) -> Unit
) {
    if (!isOpen) return
    var keyInput by remember { mutableStateOf(currentKey) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = PureWhite,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderLight, RoundedCornerShape(6.dp))
                .testTag("settings_dialog")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Agent Settings",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Zinc950
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Zinc500)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Gemini Model Selection",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Zinc700
                )
                Spacer(modifier = Modifier.height(6.dp))

                // High Thinking option
                Surface(
                    color = if (currentMode == GeminiModelMode.HIGH_THINKING) Zinc100 else PureWhite,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, if (currentMode == GeminiModelMode.HIGH_THINKING) Zinc950 else BorderLight, RoundedCornerShape(4.dp))
                        .clickable { onSetMode(GeminiModelMode.HIGH_THINKING) }
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "High Thinking (gemini-3.1-pro-preview)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Zinc950
                        )
                        Text(
                            text = "ThinkingLevel: HIGH. For complex coding queries, multi-file architecture, and deep refactoring.",
                            fontSize = 11.sp,
                            color = Zinc600
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Low Latency option
                Surface(
                    color = if (currentMode == GeminiModelMode.LOW_LATENCY) Zinc100 else PureWhite,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, if (currentMode == GeminiModelMode.LOW_LATENCY) Zinc950 else BorderLight, RoundedCornerShape(4.dp))
                        .clickable { onSetMode(GeminiModelMode.LOW_LATENCY) }
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Low Latency (gemini-3.1-flash-lite)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Zinc950
                        )
                        Text(
                            text = "Fast model for rapid edits, single-file patches, and low-latency feedback.",
                            fontSize = 11.sp,
                            color = Zinc600
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Gemini API Key (Optional Override)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Zinc700
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "By default, the key from AI Studio Secrets panel is injected automatically.",
                    fontSize = 11.sp,
                    color = Zinc500
                )
                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Zinc50)
                        .border(1.dp, BorderLight, RoundedCornerShape(4.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    BasicTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Zinc950),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (keyInput.isEmpty()) {
                        Text("AIzaSy...", fontSize = 12.sp, color = Zinc400, fontFamily = FontFamily.Monospace)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    ShadcnButton(text = "Close", onClick = onDismiss, modifier = Modifier.padding(end = 8.dp))
                    ShadcnButton(
                        text = "Save",
                        isPrimary = true,
                        onClick = {
                            onSaveKey(keyInput)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}
