package com.hermetic.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.android.EntryPointAccessors
import com.hermetic.app.di.HiltApiEntryPoint
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hermetic.app.ui.chat.ChatScreen
import com.hermetic.app.ui.chat.HermeticHexagonLogo
import com.hermetic.app.ui.explorer.ExplorerScreen
import com.hermetic.app.ui.projects.ProjectsScreen
import com.hermetic.app.ui.providers.ProvidersScreen
import com.hermetic.app.ui.sessions.SessionHistoryScreen
import com.hermetic.app.ui.settings.SettingsScreen
import com.hermetic.app.ui.theme.ActiveGreen
import kotlinx.coroutines.launch

object Routes {
    const val CHAT = "chat/{sessionId}"
    const val SESSION_HISTORY = "sessions"
    const val PROJECTS = "projects"
    const val SETTINGS = "settings"
    const val EXPLORER = "explorer/{projectPath}"
    const val PROVIDERS = "providers"

    fun chat(sessionId: String) = "chat/$sessionId"
    fun explorer(projectPath: String) = "explorer/${java.net.URLEncoder.encode(projectPath, "UTF-8")}"
}

private data class DrawerItem(
    val label: String,
    val route: String,
    val icon: ImageVector,
)

private val drawerItems = listOf(
    DrawerItem("Nuevo Chat", Routes.chat("new"), Icons.Default.Chat),
    DrawerItem("Historial", Routes.SESSION_HISTORY, Icons.Default.History),
    DrawerItem("Proyectos", Routes.PROJECTS, Icons.Default.Folder),
    DrawerItem("Ajustes", Routes.SETTINGS, Icons.Default.Settings),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HermeticNavHost() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val api = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            HiltApiEntryPoint::class.java
        ).getHermeticApi()
    }

    val navBackStackEntry = navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry.value?.destination?.route

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HermeticHexagonLogo()
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Hermetic",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                drawerItems.forEach { item ->
                    NavigationDrawerItem(
                        label = { Text(item.label) },
                        selected = false,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(Routes.CHAT) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedContainerColor = Color.Transparent,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurface,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                val isSubScreen = currentRoute == Routes.EXPLORER || currentRoute == Routes.PROVIDERS
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    title = {
                        when {
                            currentRoute?.startsWith("chat") == true -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    HermeticHexagonLogo()
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Hermetic", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                }
                            }
                            currentRoute == Routes.SESSION_HISTORY -> {
                                Text("Sesiones", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                            currentRoute == Routes.PROJECTS -> {
                                Text("Proyectos", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                            currentRoute == Routes.SETTINGS -> {
                                Text("Ajustes", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                            currentRoute == Routes.EXPLORER -> {
                                Text("Explorador", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                            currentRoute == Routes.PROVIDERS -> {
                                Text("Providers", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                        }
                    },
                    navigationIcon = {
                        if (isSubScreen) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                            }
                        } else {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menú")
                            }
                        }
                    },
                    actions = {
                        when {
                            currentRoute?.startsWith("chat") == true -> {
                                // Model selector chip on the right matching mockup 1
                                Card(
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                        .clickable { /* Choose model */ }
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(ActiveGreen, shape = CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "gpt-4o",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                            currentRoute == Routes.SESSION_HISTORY || currentRoute == Routes.PROJECTS || currentRoute == Routes.PROVIDERS -> {
                                IconButton(onClick = {
                                    if (currentRoute == Routes.SESSION_HISTORY || currentRoute?.startsWith("chat") == true) {
                                        navController.navigate(Routes.chat("new"))
                                    }
                                }) {
                                    Icon(Icons.Default.Add, contentDescription = "Añadir")
                                }
                            }
                            currentRoute == Routes.EXPLORER -> {
                                IconButton(onClick = { /* Filter */ }) {
                                    Icon(Icons.Default.Tune, contentDescription = "Filtrar")
                                }
                            }
                        }
                    }
                )
            },
            bottomBar = {
                // Show bottom navigation bar only on main screens (Chat, Projects, Settings, Providers)
                val showBottomBar = currentRoute?.startsWith("chat") == true ||
                        currentRoute == Routes.PROJECTS ||
                        currentRoute == Routes.SETTINGS ||
                        currentRoute == Routes.PROVIDERS ||
                        currentRoute == Routes.SESSION_HISTORY

                if (showBottomBar) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp,
                        modifier = Modifier.border(0.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    ) {
                        val isChatActive = currentRoute?.startsWith("chat") == true || currentRoute == Routes.SESSION_HISTORY
                        NavigationBarItem(
                            selected = isChatActive,
                            onClick = {
                                navController.navigate(Routes.chat("new")) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(if (isChatActive) Icons.Default.Chat else Icons.Outlined.Chat, contentDescription = "Chat") },
                            label = { Text("Chat") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onSurface,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )

                        val isProjectsActive = currentRoute == Routes.PROJECTS
                        NavigationBarItem(
                            selected = isProjectsActive,
                            onClick = {
                                navController.navigate(Routes.PROJECTS) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(if (isProjectsActive) Icons.Default.Folder else Icons.Outlined.Folder, contentDescription = "Proyectos") },
                            label = { Text("Proyectos") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onSurface,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )

                        val isSettingsActive = currentRoute == Routes.SETTINGS || currentRoute == Routes.PROVIDERS
                        NavigationBarItem(
                            selected = isSettingsActive,
                            onClick = {
                                navController.navigate(Routes.SETTINGS) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(if (isSettingsActive) Icons.Default.Settings else Icons.Outlined.Settings, contentDescription = "Ajustes") },
                            label = { Text("Ajustes") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onSurface,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Routes.chat("new"),
                modifier = Modifier.padding(innerPadding),
            ) {
                composable(Routes.CHAT) { backStackEntry ->
                    val sessionId = backStackEntry.arguments?.getString("sessionId") ?: "new"
                    ChatScreen(sessionId = sessionId)
                }
                composable(Routes.SESSION_HISTORY) {
                    SessionHistoryScreen(
                        api = api,
                        onSessionClick = { sessionId ->
                            navController.navigate(Routes.chat(sessionId))
                        },
                    )
                }
                composable(Routes.PROJECTS) {
                    ProjectsScreen(
                        api = api,
                        onProjectClick = { projectPath ->
                            navController.navigate(Routes.explorer(projectPath))
                        }
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onNavigateToProviders = {
                            navController.navigate(Routes.PROVIDERS)
                        },
                        onNavigateToExplorer = {
                            navController.navigate(Routes.explorer("/srv/projects/api-service"))
                        }
                    )
                }
                composable(
                    route = Routes.EXPLORER,
                    arguments = listOf(navArgument("projectPath") { type = NavType.StringType })
                ) { backStackEntry ->
                    val projectPath = backStackEntry.arguments?.getString("projectPath") ?: ""
                    ExplorerScreen(
                        api = api,
                        projectPath = java.net.URLDecoder.decode(projectPath, "UTF-8"),
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Routes.PROVIDERS) {
                    ProvidersScreen(api = api)
                }
            }
        }
    }
}
