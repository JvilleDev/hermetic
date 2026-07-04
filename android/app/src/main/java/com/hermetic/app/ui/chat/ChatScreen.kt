package com.hermetic.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hermetic.app.data.model.ChatMessage
import com.hermetic.app.data.model.MessageRole
import com.hermetic.app.data.model.ToolCallState
import com.hermetic.app.ui.theme.ActiveGreen
import com.hermetic.app.ui.theme.ActiveGreenBgLight
import com.hermetic.app.ui.theme.ActiveGreenBgDark

@Composable
fun ChatScreen(
    sessionId: String,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    // Add mock initial message for demonstration if the screen starts empty
    LaunchedEffect(Unit) {
        if (uiState.messages.isEmpty() && sessionId == "new") {
            viewModel.onInputChange("Explicame cómo funciona el sistema de autenticación en este proyecto.")
        }
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Thinking Panel when streaming or has active thinking text
        if (uiState.isStreaming || uiState.thinkingText != null) {
            ThinkingPanel(thinkingText = uiState.thinkingText ?: "Entendiendo la estructura del proyecto...\nBuscando archivos relacionados con auth...\nLeyendo el código de autenticación...")
        }

        // Messages list
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (uiState.messages.isEmpty()) {
                // If empty show a nice mock list identical to Mockup 1
                item {
                    MockConversation()
                }
            } else {
                items(uiState.messages) { msg ->
                    MessageBubble(msg)
                }
            }
        }

        InputArea(
            text = uiState.inputText,
            isStreaming = uiState.isStreaming,
            onTextChange = viewModel::onInputChange,
            onSend = {
                viewModel.sendMessage(
                    projectId = null,
                    projectName = null,
                    parentDirectory = null,
                )
            },
            onStop = viewModel::cancelStream,
        )
    }
}

// Logo Hexagon shape for the top bar
@Composable
fun HermeticHexagonLogo(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(30.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val path = Path().apply {
                moveTo(width / 2, 0f)
                lineTo(width, height / 4)
                lineTo(width, 3 * height / 4)
                lineTo(width / 2, height)
                lineTo(0f, 3 * height / 4)
                lineTo(0f, height / 4)
                close()
            }
            drawPath(
                path = path,
                color = Color.Black,
                style = Stroke(width = 4f)
            )
        }
        Text(
            text = "H",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = Color.Black
        )
    }
}

@Composable
private fun ThinkingPanel(thinkingText: String) {
    var expanded by remember { mutableStateOf(true) }
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🧠",
                            fontSize = 12.sp
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Pensando...",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    thinkingText.split("\n").forEach { step ->
                        if (step.isNotBlank()) {
                            Text(
                                text = "• $step",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Mock Tool Call executions matching mockup
                    Spacer(modifier = Modifier.height(4.dp))
                    ToolCallRow(toolName = "read_file", args = "{\"path\": \"src/auth/index.ts\"}", isRunning = true)
                    ToolCallRow(toolName = "read_file", args = "Resultado (2.1 KB)", isRunning = false)
                }
            }
        }
    }
}

@Composable
fun ToolCallRow(toolName: String, args: String, isRunning: Boolean) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isDark) Color(0xFF222326) else Color(0xFFF3F4F6),
                RoundedCornerShape(8.dp)
            )
            .border(
                0.5.dp,
                MaterialTheme.colorScheme.outline,
                RoundedCornerShape(8.dp)
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = if (isRunning) "tool_start" else "tool_result",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isRunning) Color(0xFFEA580C) else ActiveGreen
            )
            Text(
                text = toolName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = args,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (isRunning) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Success",
                tint = ActiveGreen,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun MockConversation() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // User Message
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
                    )
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Text(
                        text = "Explicame cómo funciona el sistema de autenticación en este proyecto.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "9:41 AM",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Read",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }

        // Thinking Card
        ThinkingPanel(
            thinkingText = "Entendiendo la estructura del proyecto...\nBuscando archivos relacionados con auth...\nLeyendo el código de autenticación..."
        )

        // Assistant Message
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(16.dp)
                    )
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = "El sistema de autenticación utiliza JSON Web Tokens (JWT) para manejar la autenticación de usuarios. Aquí está cómo funciona:\n\n• Registro: El usuario proporciona sus credenciales y se crea un nuevo registro en la base de datos.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage) {
    val isUser = msg.role == MessageRole.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(
                    if (isUser) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                    if (isUser) RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp) else RoundedCornerShape(16.dp)
                )
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline,
                    if (isUser) RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp) else RoundedCornerShape(16.dp)
                )
                .padding(12.dp)
        ) {
            Column {
                if (!msg.content.isNullOrEmpty()) {
                    Text(
                        text = msg.content,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                msg.toolCalls?.forEach { tc ->
                    Spacer(Modifier.height(8.dp))
                    ToolCallRow(toolName = tc.tool, args = tc.args.toString(), isRunning = tc.isRunning)
                }
            }
        }
    }
}

@Composable
private fun InputArea(
    text: String,
    isStreaming: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Attachment Button
        IconButton(
            onClick = { /* TODO */ },
            modifier = Modifier
                .size(44.dp)
                .background(MaterialTheme.colorScheme.surface, CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.AttachFile,
                contentDescription = "Adjuntar archivo",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Message input row
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Escribe tu mensaje...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            enabled = !isStreaming,
            maxLines = 4,
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.outline,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
            trailingIcon = {
                if (isStreaming) {
                    IconButton(
                        onClick = onStop,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.Red, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Detener",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = onSend,
                        enabled = text.isNotBlank(),
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                if (text.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Enviar",
                            tint = if (text.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        )
    }
}
