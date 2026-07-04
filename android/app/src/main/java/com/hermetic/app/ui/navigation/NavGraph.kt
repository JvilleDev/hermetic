package com.hermetic.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Key
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
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.android.EntryPointAccessors
import com.hermetic.app.di.HiltApiEntryPoint
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.hermetic.app.data.model.Provider
import com.hermetic.app.data.model.Session
import com.hermetic.app.data.remote.HermeticApi
import com.hermetic.app.data.repository.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import com.hermetic.app.ui.chat.ChatScreen
import com.hermetic.app.ui.chat.HermeticHexagonLogo
import com.hermetic.app.ui.explorer.ExplorerScreen
import com.hermetic.app.ui.projects.ProjectsScreen
import com.hermetic.app.ui.providers.ProvidersScreen
import com.hermetic.app.ui.sessions.SessionHistoryScreen
import com.hermetic.app.ui.login.LoginScreen
import com.hermetic.app.ui.settings.SettingsScreen
import com.hermetic.app.ui.theme.ActiveGreen
import kotlinx.coroutines.launch

object Routes {
    const val LOGIN = "login"
    const val CHAT = "chat/{sessionId}?parentDirectory={parentDirectory}&projectId={projectId}&projectName={projectName}"
    const val SESSION_HISTORY = "sessions"
    const val PROJECTS = "projects"
    const val SETTINGS = "settings"
    const val EXPLORER = "explorer/{projectPath}"
    const val PROVIDERS = "providers"

    fun chat(
        sessionId: String,
        parentDirectory: String? = null,
        projectId: String? = null,
        projectName: String? = null
    ): String {
        var path = "chat/$sessionId"
        val params = mutableListOf<String>()
        if (parentDirectory != null) {
            params.add("parentDirectory=${java.net.URLEncoder.encode(parentDirectory, "UTF-8")}")
        }
        if (projectId != null) {
            params.add("projectId=${java.net.URLEncoder.encode(projectId, "UTF-8")}")
        }
        if (projectName != null) {
            params.add("projectName=${java.net.URLEncoder.encode(projectName, "UTF-8")}")
        }
        if (params.isNotEmpty()) {
            path += "?" + params.joinToString("&")
        }
        return path
    }

    fun explorer(projectPath: String) = "explorer/${java.net.URLEncoder.encode(projectPath, "UTF-8")}"
}

private data class NavigationItemData(
    val label: String,
    val route: String,
    val icon: ImageVector,
    val isSelected: Boolean
)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HermeticNavHost() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val entryPoint = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            HiltApiEntryPoint::class.java
        )
    }
    val api = remember { entryPoint.getHermeticApi() }
    val authManager = remember { entryPoint.getAuthManager() }
    val chatRepository = remember { entryPoint.getChatRepository() }

    var isLoggedIn by remember { mutableStateOf(authManager.isLoggedIn) }

    if (!isLoggedIn) {
        LoginScreen(
            authManager = authManager,
            onLoginSuccess = {
                isLoggedIn = true
            }
        )
        return
    }

    val navBackStackEntry = navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry.value?.destination?.route

    val sessions by chatRepository.sessions.collectAsState()
    val activeModel by chatRepository.activeModel.collectAsState()
    val providers by chatRepository.providers.collectAsState()

    // Prefetch sessions asynchronously as soon as the app loads
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            chatRepository.refreshSessions()
            chatRepository.refreshActiveModel()
        }
    }

    // Refresh sessions asynchronously when the drawer is opened
    LaunchedEffect(drawerState.currentValue) {
        if (drawerState.isOpen) {
            withContext(Dispatchers.IO) {
                chatRepository.refreshSessions()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                                val currentSessionId = navBackStackEntry.value?.arguments?.getString("sessionId")
                                val currentSession = sessions.find { it.id == currentSessionId }
                                val displayTitle = if (currentSession != null && currentSession.title != "Nuevo chat" && currentSession.title.isNotBlank()) {
                                    currentSession.title
                                } else {
                                    ""
                                }
                                AnimatedVisibility(
                                    visible = displayTitle.isNotEmpty(),
                                    enter = fadeIn(animationSpec = tween(400)) + expandHorizontally(),
                                    exit = fadeOut(animationSpec = tween(400)) + shrinkHorizontally()
                                ) {
                                    Text(
                                        text = displayTitle,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
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
                                Icon(Icons.Outlined.ArrowBack, contentDescription = "Volver")
                            }
                        } else {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Outlined.Menu, contentDescription = "Menú")
                            }
                        }
                    },
                    actions = {
                        when {
                            currentRoute == Routes.EXPLORER -> {
                                IconButton(onClick = { /* Filter */ }) {
                                    Icon(Icons.Default.Tune, contentDescription = "Filtrar")
                                }
                            }
                        }
                    }
                )
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Routes.chat("new"),
                modifier = Modifier.padding(innerPadding),
            ) {
                composable(
                    route = Routes.CHAT,
                    arguments = listOf(
                        navArgument("sessionId") { type = NavType.StringType },
                        navArgument("parentDirectory") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                        navArgument("projectId") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                        navArgument("projectName") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    ),
                    enterTransition = { fadeIn(tween(0)) },
                    exitTransition = { fadeOut(tween(0)) },
                    popEnterTransition = { fadeIn(tween(0)) },
                    popExitTransition = { fadeOut(tween(0)) },
                ) { backStackEntry ->
                    val sessionId = backStackEntry.arguments?.getString("sessionId") ?: "new"
                    ChatScreen(sessionId = sessionId)
                }
                composable(
                    Routes.SESSION_HISTORY,
                    enterTransition = { fadeIn(tween(0)) },
                    exitTransition = { fadeOut(tween(0)) },
                    popEnterTransition = { fadeIn(tween(0)) },
                    popExitTransition = { fadeOut(tween(0)) },
                ) {
                    SessionHistoryScreen(
                        api = api,
                        onSessionClick = { sessionId ->
                            navController.navigate(Routes.chat(sessionId))
                        },
                    )
                }
                composable(
                    Routes.PROJECTS,
                    enterTransition = { fadeIn(tween(0)) },
                    exitTransition = { fadeOut(tween(0)) },
                    popEnterTransition = { fadeIn(tween(0)) },
                    popExitTransition = { fadeOut(tween(0)) },
                ) {
                    ProjectsScreen(
                        api = api,
                        onProjectClick = { projectPath ->
                            navController.navigate(Routes.explorer(projectPath))
                        }
                    )
                }
                composable(
                    Routes.SETTINGS,
                    enterTransition = { fadeIn(tween(0)) },
                    exitTransition = { fadeOut(tween(0)) },
                    popEnterTransition = { fadeIn(tween(0)) },
                    popExitTransition = { fadeOut(tween(0)) },
                ) {
                    SettingsScreen(
                        authManager = authManager,
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
                    arguments = listOf(navArgument("projectPath") { type = NavType.StringType }),
                    enterTransition = { fadeIn(tween(0)) },
                    exitTransition = { fadeOut(tween(0)) },
                    popEnterTransition = { fadeIn(tween(0)) },
                    popExitTransition = { fadeOut(tween(0)) },
                ) { backStackEntry ->
                    val projectPath = backStackEntry.arguments?.getString("projectPath") ?: ""
                    ExplorerScreen(
                        api = api,
                        projectPath = java.net.URLDecoder.decode(projectPath, "UTF-8"),
                        onBack = { navController.popBackStack() },
                        onStartChat = { parentDir ->
                            navController.navigate(Routes.chat("new", parentDirectory = parentDir))
                        }
                    )
                }
                composable(
                    Routes.PROVIDERS,
                    enterTransition = { fadeIn(tween(0)) },
                    exitTransition = { fadeOut(tween(0)) },
                    popEnterTransition = { fadeIn(tween(0)) },
                    popExitTransition = { fadeOut(tween(0)) },
                ) {
                    ProvidersScreen(api = api)
                }
            }
        }

        // Custom iOS-style Side Sheet Panel Overlay
        AnimatedVisibility(
            visible = drawerState.isOpen,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {
                        scope.launch { drawerState.close() }
                    }
            )
        }

        AnimatedVisibility(
            visible = drawerState.isOpen,
            enter = slideInHorizontally(
                animationSpec = tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            ) { -it },
            exit = slideOutHorizontally(
                animationSpec = tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            ) { -it }
        ) {
            val isDark = androidx.compose.foundation.isSystemInDarkTheme()
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(280.dp)
                    .background(if (isDark) Color(0xFF0F0F10) else Color(0xFFF2F2F7))
                    .border(
                        1.dp,
                        if (isDark) Color(0xFF1E1E20) else Color(0xFFE5E5EA),
                        RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
                    )
                    .clip(RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
            ) {
                DrawerContent(
                    api = api,
                    navController = navController,
                    drawerState = drawerState,
                    scope = scope,
                    sessions = sessions
                )
            }
        }
    }
}

@Composable
private fun DrawerContent(
    api: HermeticApi,
    navController: NavHostController,
    drawerState: DrawerState,
    scope: CoroutineScope,
    sessions: List<Session>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = com.hermetic.app.R.mipmap.ic_launcher),
                    contentDescription = "Hermetic Logo",
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Hermetic",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = { /* Search */ }) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "Buscar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Upper navigation section (using modern iOS style outline icons)
        val navItems = listOf(
            Triple("Proyectos", Routes.PROJECTS, Icons.Outlined.Folder),
            Triple("Ajustes", Routes.SETTINGS, Icons.Outlined.Settings),
            Triple("Providers", Routes.PROVIDERS, Icons.Outlined.Key)
        )

        navItems.forEach { (label, route, icon) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        navController.navigate(route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                        scope.launch { drawerState.close() }
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = label,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Recientes",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
        )

        // Recents sessions list (scrollable)
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(sessions) { session ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            navController.navigate(Routes.chat(session.id))
                            scope.launch { drawerState.close() }
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = session.title,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        Spacer(modifier = Modifier.height(16.dp))

        // Bottom section of drawer (New Chat button + user avatar)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Nuevo Chat button (pill shape in green/accent color)
            Button(
                onClick = {
                    navController.navigate(Routes.chat("new"))
                    scope.launch { drawerState.close() }
                },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ActiveGreen),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = Modifier.height(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Nuevo Chat",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // User Avatar
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "JV",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
