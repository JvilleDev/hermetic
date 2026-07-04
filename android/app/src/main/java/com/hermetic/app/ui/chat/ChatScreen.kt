package com.hermetic.app.ui.chat

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.animation.core.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hermetic.app.data.model.ChatMessage
import com.hermetic.app.data.model.MessageRole
import com.hermetic.app.data.model.ToolCallState
import com.hermetic.app.data.model.ProviderWithModels
import com.hermetic.app.ui.theme.ActiveGreen
import com.hermetic.app.ui.theme.ActiveGreenBgLight
import com.hermetic.app.ui.theme.ActiveGreenBgDark
import com.hermetic.app.ui.components.MarkdownText

@Composable
fun ChatScreen(
    sessionId: String,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val activeModel by viewModel.activeModel.collectAsState()
    val providers by viewModel.providers.collectAsState()
    val listState = rememberLazyListState()

    val lastMessage = uiState.messages.lastOrNull()
    val lastContent = lastMessage?.content
    val lastToolCallsSize = lastMessage?.toolCalls?.size ?: 0
    LaunchedEffect(uiState.messages.size, lastContent, lastToolCallsSize) {
        if (uiState.messages.isNotEmpty()) {
            if (uiState.isStreaming) {
                listState.scrollToItem(uiState.messages.size - 1)
            } else {
                listState.animateScrollToItem(uiState.messages.size - 1)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        // Active Context Banner
        uiState.parentDirectory?.let { dir ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Contexto: $dir",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    fontFamily = FontFamily.Monospace
                )
            }
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
            if (uiState.isLoading) {
                items(3) {
                    MessageSkeleton()
                }
            } else if (uiState.messages.isEmpty()) {
                item {
                    WelcomeScreen(uiState.parentDirectory, onSuggestClick = viewModel::onInputChange)
                }
            } else {
                items(uiState.messages) { msg ->
                    val isLast = uiState.messages.lastOrNull() == msg
                    MessageBubble(
                        msg = msg,
                        isLastAndStreaming = isLast && uiState.isStreaming
                    )
                }
            }
        }

        InputArea(
            text = uiState.inputText,
            isStreaming = uiState.isStreaming,
            onTextChange = viewModel::onInputChange,
            onSend = {
                viewModel.sendMessage()
            },
            onStop = viewModel::cancelStream,
            activeModel = activeModel,
            selectedProviderId = uiState.selectedProviderId,
            selectedModel = uiState.selectedModel,
            providers = providers,
            onSelectProvider = viewModel::selectProvider,
        )
    }
}

@Composable
private fun WelcomeScreen(
    parentDirectory: String?,
    onSuggestClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 80.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        HermeticHexagonLogo(modifier = Modifier.size(100.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Hermetic",
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// Logo Hexagon shape for the top bar (replaced with transparent brand logo asset)
@Composable
fun HermeticHexagonLogo(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(id = com.hermetic.app.R.drawable.ic_logo_no_bg),
        contentDescription = "Hermetic Logo",
        modifier = modifier
    )
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
                    imageVector = if (expanded) Icons.Outlined.KeyboardArrowDown else Icons.Outlined.ChevronRight,
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
fun ToolCallRow(
    toolName: String,
    args: String,
    isRunning: Boolean,
    result: String? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isDark) Color(0xFF16171A) else Color(0xFFF3F4F6))
            .clickable { expanded = !expanded }
            .padding(vertical = 6.dp, horizontal = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = ">",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )

                Text(
                    text = "Ran $toolName",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(10.dp),
                        strokeWidth = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Running...",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = "Success",
                        tint = ActiveGreen,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "Done",
                        fontSize = 10.sp,
                        color = ActiveGreen
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            ) {
                if (args.isNotBlank() && args != "{}") {
                    Text(
                        text = "Arguments:",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Text(
                        text = args,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isDark) Color(0xFF1E1F22) else Color(0xFFE5E7EB),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(6.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (!result.isNullOrBlank()) {
                    Text(
                        text = "Result:",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Text(
                        text = result,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isDark) Color(0xFF1E1F22) else Color(0xFFE5E7EB),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(6.dp)
                    )
                }
            }
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
                            imageVector = Icons.Outlined.Check,
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
private fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    val alpha2 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    val alpha3 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Box(modifier = Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = alpha1), CircleShape))
        Box(modifier = Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = alpha2), CircleShape))
        Box(modifier = Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = alpha3), CircleShape))
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage, isLastAndStreaming: Boolean = false) {
    val isUser = msg.role == MessageRole.USER
    val isStreaming = isLastAndStreaming && !isUser
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    var displayedContent by remember(msg.timestamp) {
        mutableStateOf(if (isStreaming) "" else (msg.content ?: ""))
    }

    if (isStreaming) {
        LaunchedEffect(msg.content) {
            val target = msg.content ?: ""
            if (target.length < displayedContent.length) {
                displayedContent = target
            } else {
                while (displayedContent.length < target.length) {
                    val increment = if (target.length - displayedContent.length > 20) 4 else 2
                    val nextLength = (displayedContent.length + increment).coerceAtMost(target.length)
                    displayedContent = target.substring(0, nextLength)
                    kotlinx.coroutines.delay(10)
                }
            }
        }
    } else {
        displayedContent = msg.content ?: ""
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (!isUser && !msg.thinking.isNullOrBlank()) {
            var thinkingExpanded by remember { mutableStateOf(true) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    .background(
                        if (isDark) Color(0xFF1E1E1E) else Color(0xFFF2F2F7),
                        RoundedCornerShape(8.dp)
                    )
                    .border(
                        1.dp,
                        if (isDark) Color(0xFF2D2D30) else Color(0xFFE5E5EA),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { thinkingExpanded = !thinkingExpanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Psychology,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Proceso de pensamiento",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = if (thinkingExpanded) Icons.Outlined.KeyboardArrowDown else Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
                
                if (thinkingExpanded) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = msg.thinking,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (displayedContent.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .then(
                        if (isUser) Modifier.widthIn(max = 280.dp).align(Alignment.End)
                        else Modifier.fillMaxWidth().align(Alignment.Start)
                    )
                    .padding(horizontal = 4.dp)
            ) {
                Column {
                    MarkdownText(
                        markdown = displayedContent,
                        color = if (isUser) {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    if (!isUser) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 2.dp),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val context = androidx.compose.ui.platform.LocalContext.current
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("Hermetic Response", displayedContent)
                                    clipboard.setPrimaryClip(clip)
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = "Copiar respuesta",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            if (msg.durationSeconds != null) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${msg.durationSeconds}s",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                            if (!msg.model.isNullOrBlank()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = msg.model,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                )
                            }
                        }
                    }
                }
            }
        } else if (isLastAndStreaming && !isUser && msg.toolCalls.isNullOrEmpty()) {
            TypingIndicator()
        }

        msg.toolCalls?.forEach { tc ->
            val tcVisibleState = remember(tc.tool) {
                MutableTransitionState(false).apply {
                    targetState = true
                }
            }
            AnimatedVisibility(
                visibleState = tcVisibleState,
                enter = fadeIn(animationSpec = tween(300)) + expandVertically(animationSpec = tween(300)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Spacer(Modifier.height(6.dp))
                    ToolCallRow(
                        toolName = tc.tool,
                        args = tc.args.toString(),
                        isRunning = tc.isRunning,
                        result = tc.result
                    )
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
    activeModel: String,
    selectedProviderId: String? = null,
    selectedModel: String? = null,
    providers: List<ProviderWithModels>,
    onSelectProvider: (String, String) -> Unit
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .background(
                color = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF2F2F7),
                shape = RoundedCornerShape(28.dp)
            )
            .border(
                1.dp,
                if (isDark) Color(0xFF2D2D30) else Color(0xFFE5E5EA),
                RoundedCornerShape(28.dp)
            )
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Model Selector — compact pill trigger
        var menuExpanded by remember { mutableStateOf(false) }
        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (isDark) Color(0xFF25262B) else Color(0xFFF3F4F6))
                    .clickable { menuExpanded = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Psychology,
                    contentDescription = "Seleccionar modelo",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = selectedModel ?: activeModel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 80.dp)
                )
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp)
                )
            }

            ModelSelectorPopup(
                menuExpanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                providers = providers,
                selectedProviderId = selectedProviderId,
                selectedModel = selectedModel,
                activeModel = activeModel,
                onSelectProvider = onSelectProvider,
                isDark = isDark
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Text Field inside capsule
        androidx.compose.foundation.text.BasicTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (text.isEmpty()) {
                        Text(
                            text = "Pregunta a Hermetic...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    innerTextField()
                }
            }
        )

        // Send / Stop button inside the capsule (using Box to bypass IconButton's 48.dp touch target enforcement)
        val isEnabled = isStreaming || text.isNotBlank()
        Box(
            modifier = Modifier
                .padding(start = 6.dp, end = 4.dp)
                .size(32.dp)
                .background(
                    color = when {
                        isStreaming -> if (isDark) Color.White else Color.Black
                        text.isNotBlank() -> if (isDark) Color.White else Color.Black
                        else -> Color.Transparent
                    },
                    shape = CircleShape
                )
                .then(
                    if (isEnabled) {
                        Modifier.clickable {
                            if (isStreaming) {
                                onStop()
                            } else {
                                onSend()
                            }
                        }
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isStreaming) Icons.Outlined.Stop else Icons.Outlined.ArrowUpward,
                contentDescription = if (isStreaming) "Detener" else "Enviar",
                tint = when {
                    isStreaming || text.isNotBlank() -> if (isDark) Color.Black else Color.White
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                },
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun ModelSelectorPopup(
    menuExpanded: Boolean,
    onDismissRequest: () -> Unit,
    providers: List<ProviderWithModels>,
    selectedProviderId: String?,
    selectedModel: String?,
    activeModel: String,
    onSelectProvider: (String, String) -> Unit,
    isDark: Boolean
) {
    val density = LocalDensity.current
    AnimatedVisibility(
        visible = menuExpanded,
        enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { -it / 3 },
        exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { -it / 3 }
    ) {
        Popup(
            onDismissRequest = onDismissRequest,
            properties = PopupProperties(focusable = true),
            alignment = Alignment.BottomStart,
            offset = with(density) { IntOffset(4.dp.roundToPx(), (-12).dp.roundToPx()) }
        ) {
            Card(
                modifier = Modifier
                    .widthIn(min = 200.dp, max = 320.dp)
                    .heightIn(max = 380.dp)
                    .border(
                        1.dp,
                        if (isDark) Color(0xFF2D2E32) else Color(0xFFE5E7EB),
                        RoundedCornerShape(24.dp)
                    ),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF18191B) else Color(0xFFFFFFFF)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .verticalScroll(scrollState)
                        .requiredHeightIn(max = 380.dp)
                ) {
                    var stepProviderId by remember { mutableStateOf<String?>(null) }
                    val hasProviders = providers.isNotEmpty() && providers.any { it.models.isNotEmpty() }

                    if (!hasProviders) {
                        Text(
                            text = "No hay providers disponibles",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    } else if (stepProviderId == null) {
                        // Step 1 — pick a provider
                        providers.forEach { provider ->
                            if (provider.models.isEmpty()) return@forEach

                            val isActiveProvider = if (selectedProviderId != null)
                                provider.id == selectedProviderId
                            else if (selectedModel != null)
                                provider.models.any { it == selectedModel }
                            else
                                false

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { stepProviderId = provider.id }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Psychology,
                                    contentDescription = null,
                                    tint = if (isActiveProvider) ActiveGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = provider.name,
                                        fontSize = 13.sp,
                                        fontWeight = if (isActiveProvider) FontWeight.SemiBold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${provider.models.size} modelo${if (provider.models.size != 1) "s" else ""}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                                if (isActiveProvider) {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = null,
                                        tint = ActiveGreen,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                }
                                Spacer(Modifier.width(2.dp))
                                Icon(
                                    imageVector = Icons.Outlined.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    } else {
                        // Step 2 — pick a model from the selected provider
                        val selectedProv = providers.find { it.id == stepProviderId }
                        if (selectedProv != null) {
                            // Back row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { stepProviderId = null }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                                        contentDescription = "Volver",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = selectedProv.name,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
                            )

                            selectedProv.models.forEach { model ->
                                val currentSelected = if (selectedModel != null)
                                    model == selectedModel && stepProviderId == selectedProviderId
                                else
                                    false

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                                onDismissRequest()
                                                stepProviderId = null
                                            onSelectProvider(selectedProv.id, model)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(
                                                if (currentSelected) ActiveGreen else Color.Transparent,
                                                CircleShape
                                            )
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = model,
                                        fontSize = 12.sp,
                                        fontWeight = if (currentSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (currentSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (currentSelected) {
                                        Icon(
                                            imageVector = Icons.Outlined.Check,
                                            contentDescription = null,
                                            tint = ActiveGreen,
                                            modifier = Modifier.size(14.dp)
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
}

@Composable
private fun MessageSkeleton() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // User message skeleton (right aligned)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(40.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha),
                        shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
                    )
            )
        }

        // Assistant message skeleton (left aligned)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Column(
                modifier = Modifier
                    .width(260.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = alpha),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = alpha),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .background(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        )
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(16.dp)
                        .background(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        )
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(16.dp)
                        .background(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        )
                )
            }
        }
    }
}
