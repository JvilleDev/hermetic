package com.hermetic.app.ui.providers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermetic.app.data.model.Provider
import com.hermetic.app.data.model.ProviderType
import com.hermetic.app.data.remote.HermeticApi
import com.hermetic.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProvidersScreen(api: HermeticApi) {
    var providers by remember { mutableStateOf<List<Provider>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showDialog by remember { mutableStateOf(false) }
    var editingProvider by remember { mutableStateOf<Provider?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<Provider?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        withContext(Dispatchers.IO) {
            api.getProviders()
        }.onSuccess {
            providers = it
            isLoading = false
        }.onFailure {
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            if (!isLoading) {
                FloatingActionButton(
                    onClick = {
                        editingProvider = null
                        showDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Añadir provider")
                }
            }
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(providers, key = { it.id }) { provider ->
                    ProviderCard(
                        provider = provider,
                        onEdit = {
                            editingProvider = provider
                            showDialog = true
                        },
                        onDelete = { showDeleteConfirm = provider }
                    )
                }

                if (providers.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Toca + para añadir un provider",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        ProviderFormDialog(
            provider = editingProvider,
            onDismiss = { showDialog = false },
            onSave = { data ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        if (editingProvider != null) {
                            api.updateProvider(editingProvider!!.id, data)
                        } else {
                            api.createProvider(data)
                        }
                    }
                    showDialog = false
                    isLoading = true
                    load()
                }
            }
        )
    }

    showDeleteConfirm?.let { provider ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Eliminar provider") },
            text = { Text("¿Eliminar \"${provider.name}\" permanentemente?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                api.deleteProvider(provider.id)
                            }
                            showDeleteConfirm = null
                            isLoading = true
                            load()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
fun ProviderCard(
    provider: Provider,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val (logoChar, logoBg, logoTextCol) = when (provider.providerType) {
        ProviderType.OPENAI -> Triple("O", Color(0xFF10a37f), Color.White)
        ProviderType.ANTHROPIC -> Triple("A", Color(0xFFfcfaf7), Color(0xFFcc785c))
        ProviderType.GEMINI -> Triple("G", Color(0xFFEFF6FF), Color(0xFF1A73E8))
    }
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(logoBg, CircleShape)
                    .border(1.dp, logoTextCol.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = logoChar, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = logoTextCol)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = provider.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = provider.defaultModel,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (provider.isDefault) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(if (isDark) ActiveGreenBgDark else ActiveGreenBgLight, RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "def",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = ActiveGreen
                            )
                        }
                    }
                }
            }

            val badgeBg = if (provider.isActive) {
                if (isDark) ActiveGreenBgDark else ActiveGreenBgLight
            } else {
                if (isDark) InactiveGrayBgDark else InactiveGrayBgLight
            }
            val badgeTextCol = if (provider.isActive) ActiveGreen else InactiveGray
            val label = if (provider.isActive) "Activo" else "Inactivo"

            Box(
                modifier = Modifier
                    .background(badgeBg, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = badgeTextCol)
            }

            Spacer(Modifier.width(8.dp))

            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = "Editar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Eliminar",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun ProviderFormDialog(
    provider: Provider?,
    onDismiss: () -> Unit,
    onSave: (Map<String, Any>) -> Unit,
) {
    var name by remember { mutableStateOf(provider?.name ?: "") }
    var providerType by remember { mutableStateOf(provider?.providerType?.name?.lowercase() ?: "openai") }
    var apiKey by remember { mutableStateOf(provider?.apiKey ?: "") }
    var baseUrl by remember { mutableStateOf(provider?.baseUrl ?: "") }
    var defaultModel by remember { mutableStateOf(provider?.defaultModel ?: "") }
    var maxContextTokens by remember { mutableStateOf(provider?.maxContextTokens?.toString() ?: "") }
    var isActive by remember { mutableStateOf(provider?.isActive ?: true) }
    var isDefault by remember { mutableStateOf(provider?.isDefault ?: false) }
    var showKey by remember { mutableStateOf(false) }
    var typeExpanded by remember { mutableStateOf(false) }

    val isEditing = provider != null
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    val types = listOf("gemini", "openai", "anthropic")

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = if (isDark) Color(0xFF18191B) else Color(0xFFFFFFFF),
        title = {
            Text(
                text = if (isEditing) "Editar provider" else "Nuevo provider",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Provider type dropdown (stable DropdownMenu used as simple selector)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = providerType.replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Tipo") },
                        trailingIcon = {
                            IconButton(onClick = { typeExpanded = true }) {
                                Icon(
                                    Icons.Outlined.KeyboardArrowDown,
                                    contentDescription = "Seleccionar tipo",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { typeExpanded = true },
                        shape = RoundedCornerShape(12.dp)
                    )
                    DropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        types.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t.replaceFirstChar { it.uppercase() }) },
                                onClick = {
                                    providerType = t
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(
                            onClick = { showKey = !showKey },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (showKey) "Ocultar" else "Mostrar",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL (opcional)") },
                    placeholder = { Text("https://...", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = defaultModel,
                    onValueChange = { defaultModel = it },
                    label = { Text("Modelo por defecto") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = maxContextTokens,
                    onValueChange = { maxContextTokens = it.filter { c -> c.isDigit() } },
                    label = { Text("Máx. tokens contexto (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Activo", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Provider por defecto", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    Switch(checked = isDefault, onCheckedChange = { isDefault = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val data = mutableMapOf<String, Any>(
                        "name" to name,
                        "provider_type" to providerType,
                        "api_key" to apiKey,
                        "default_model" to defaultModel,
                        "is_active" to isActive,
                        "is_default" to isDefault,
                    )
                    if (baseUrl.isNotBlank()) data["base_url"] = baseUrl
                    if (maxContextTokens.isNotBlank()) data["max_context_tokens"] = maxContextTokens.toInt()
                    onSave(data)
                },
                enabled = name.isNotBlank() && apiKey.isNotBlank() && defaultModel.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (isEditing) "Guardar" else "Crear")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
