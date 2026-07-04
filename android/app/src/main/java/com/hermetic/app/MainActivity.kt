package com.hermetic.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermetic.app.ui.navigation.HermeticNavHost
import com.hermetic.app.ui.theme.HermeticTheme
import com.hermetic.app.updater.UpdateManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.navigationBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        setContent {
            HermeticTheme {
                val context = androidx.compose.ui.platform.LocalContext.current
                val updateManager = remember { UpdateManager(context) }
                var updateUrl by remember { mutableStateOf<String?>(null) }
                var isDownloading by remember { mutableStateOf(false) }
                var downloadProgress by remember { mutableStateOf(0f) }
                val scope = rememberCoroutineScope()

                LaunchedEffect(Unit) {
                    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                    val currentVersion = packageInfo.versionName ?: "0.1.0"
                    updateUrl = updateManager.checkForUpdates(currentVersion)
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
                                        updateManager.downloadAndInstallApk(updateUrl!!) { progress ->
                                            downloadProgress = progress
                                        }.onSuccess {
                                            updateUrl = null
                                            isDownloading = false
                                        }.onFailure {
                                            isDownloading = false
                                        }
                                    }
                                }
                            ) {
                                Text("Actualizar")
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

                HermeticNavHost()
            }
        }
    }
}
