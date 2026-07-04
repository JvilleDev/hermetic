package com.hermetic.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermetic.app.ui.theme.ActiveGreen
import com.hermetic.app.ui.theme.ActiveGreenBgLight
import com.hermetic.app.ui.theme.ActiveGreenBgDark
import com.hermetic.app.ui.theme.InactiveGrayBgLight
import com.hermetic.app.ui.theme.InactiveGrayBgDark
import com.hermetic.app.ui.theme.InactiveGray
import com.hermetic.app.updater.UpdateManager
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@Composable
fun SettingsScreen(
    onNavigateToProviders: () -> Unit,
    onNavigateToExplorer: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var backendUrl by remember { mutableStateOf("http://144.217.161.133:9876") }
    var isConnected by remember { mutableStateOf<Boolean?>(true) } // Mock connected initially like the mockup
    var isChecking by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var updateUrl by remember { mutableStateOf<String?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    val updateManager = remember { UpdateManager(context) }
    val packageInfo = remember {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    val appVersion = remember { packageInfo.versionName ?: "0.1.0" }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    fun checkHealth(url: String) {
        isChecking = true
        scope.launch {
            val result = runCatching {
                withTimeout(5000) {
                    withContext(Dispatchers.IO) {
                        val entryPoint = EntryPointAccessors.fromApplication(
                            context.applicationContext,
                            com.hermetic.app.di.HiltApiEntryPoint::class.java
                        )
                        val api = entryPoint.getHermeticApi()
                        api.setBaseUrl(url)
                        api.healthCheck()
                    }
                }
            }
            isConnected = result.getOrNull()?.getOrNull() == true
            isChecking = false
        }
    }

    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("Configurar Servidor") },
            text = {
                Column {
                    Text("Ingresa la URL de tu VPS o servidor backend:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = backendUrl,
                        onValueChange = { backendUrl = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    showUrlDialog = false
                    checkHealth(backendUrl)
                }) {
                    Text("Guardar y Verificar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (updateUrl != null) {
        AlertDialog(
            onDismissRequest = { if (!isDownloading) updateUrl = null },
            title = { Text("Actualización Disponible") },
            text = {
                Column {
                    Text("Hay una nueva versión de Hermetic disponible para instalar.")
                    if (isDownloading) {
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Descargando: ${(downloadProgress * 100).toInt()}%", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isDownloading,
                    onClick = {
                        isDownloading = true
                        scope.launch {
                            val result = updateManager.downloadAndInstallApk(
                                url = updateUrl!!,
                                onProgress = { downloadProgress = it }
                            )
                            if (result.isFailure) {
                                isDownloading = false
                                updateUrl = null
                            }
                        }
                    }
                ) {
                    Text(if (isDownloading) "Descargando..." else "Descargar e Instalar")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isDownloading,
                    onClick = { updateUrl = null }
                ) {
                    Text("Más tarde")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Section: Conectividad
        Text(
            text = "CONECTIVIDAD",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column {
                // Estado del servidor
                val isDark = androidx.compose.foundation.isSystemInDarkTheme()
                val statusText = if (isChecking) "Verificando..." else if (isConnected == true) "Conectado" else "Desconectado"
                val statusColor = if (isConnected == true) ActiveGreen else InactiveGray
                val statusBg = if (isConnected == true) {
                    if (isDark) ActiveGreenBgDark else ActiveGreenBgLight
                } else {
                    if (isDark) InactiveGrayBgDark else InactiveGrayBgLight
                }

                SettingsRow(
                    title = "Estado del servidor",
                    subtitle = statusText,
                    onClick = { checkHealth(backendUrl) },
                    customContent = {
                        Box(
                            modifier = Modifier
                                .background(statusBg, CircleShape)
                                .size(8.dp)
                        )
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))

                // VPS
                SettingsRow(
                    title = "VPS",
                    subtitle = backendUrl,
                    onClick = { showUrlDialog = true }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Section: Gestión
        Text(
            text = "GESTIÓN",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column {
                SettingsRow(
                    title = "Providers",
                    subtitle = "Modelos de IA y API Keys",
                    icon = Icons.Default.Key,
                    onClick = onNavigateToProviders
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))

                SettingsRow(
                    title = "Servidores MCP",
                    subtitle = "Conectar herramientas",
                    icon = Icons.Default.Dns,
                    onClick = { /* TODO */ }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))

                SettingsRow(
                    title = "Skills",
                    subtitle = "Skills personalizadas",
                    icon = Icons.Default.Extension,
                    onClick = { /* TODO */ }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))

                SettingsRow(
                    title = "Explorador de archivos",
                    subtitle = "Navegar directorios",
                    icon = Icons.Default.Folder,
                    onClick = onNavigateToExplorer
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))

                SettingsRow(
                    title = "Acerca de Hermetic",
                    subtitle = "v$appVersion",
                    icon = Icons.Default.Info,
                    onClick = {
                        scope.launch {
                            updateUrl = updateManager.checkForUpdates(appVersion)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun SettingsRow(
    title: String,
    subtitle: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    customContent: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (customContent != null) {
                    customContent()
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Navigate",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
    }
}
