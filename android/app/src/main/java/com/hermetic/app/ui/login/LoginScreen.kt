package com.hermetic.app.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermetic.app.auth.AuthManager
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    authManager: AuthManager,
    onLoginSuccess: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    var showHostField by remember { mutableStateOf(!authManager.hasHost) }
    var hostUrl by remember { mutableStateOf(authManager.hostUrl) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var isRegistering by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun doLogin() {
        focusManager.clearFocus()
        isLoading = true
        error = null
        scope.launch {
            val result = authManager.login(email.trim(), password)
            isLoading = false
            result.fold(
                onSuccess = { onLoginSuccess() },
                onFailure = { e -> error = e.message ?: "Error al iniciar sesión" }
            )
        }
    }

    fun doRegister() {
        focusManager.clearFocus()
        isLoading = true
        error = null
        scope.launch {
            val result = authManager.register(email.trim(), password, name.trim())
            isLoading = false
            result.fold(
                onSuccess = { onLoginSuccess() },
                onFailure = { e -> error = e.message ?: "Error al registrarse" }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 400.dp),
        ) {
            Text(
                text = "Hermetic",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Motor de IA soberano personal",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (showHostField) {
                OutlinedTextField(
                    value = hostUrl,
                    onValueChange = { hostUrl = it },
                    label = { Text("URL del servidor") },
                    placeholder = { Text("https://ejemplo.com:9876") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        if (hostUrl.isNotBlank()) {
                            authManager.setHost(hostUrl)
                            showHostField = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Conectar")
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(24.dp))
            }

            if (!showHostField) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = if (isRegistering) ImeAction.Next else ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (!isRegistering) doLogin() }),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (isRegistering) {
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nombre (opcional)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { doRegister() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (error != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { if (isRegistering) doRegister() else doLogin() },
                    enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(if (isRegistering) "Crear Cuenta" else "Iniciar Sesión")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = {
                        isRegistering = !isRegistering
                        error = null
                    },
                ) {
                    Text(
                        if (isRegistering) "Ya tengo cuenta" else "Crear cuenta nueva",
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}