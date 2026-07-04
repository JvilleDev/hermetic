package com.hermetic.app.ui.explorer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermetic.app.data.remote.HermeticApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class ExplorerNode {
    abstract val name: String
    abstract val depth: Int

    data class Directory(
        override val name: String,
        override val depth: Int,
        val isOpen: Boolean = false,
    ) : ExplorerNode()

    data class File(
        override val name: String,
        override val depth: Int,
        val extension: String = "",
    ) : ExplorerNode()
}

@Composable
fun ExplorerScreen(
    api: HermeticApi,
    projectPath: String,
    onBack: () -> Unit,
) {
    var selectedTab by remember { mutableStateOf("Árbol") }
    var nodes by remember { mutableStateOf<List<ExplorerNode>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(projectPath) {
        withContext(Dispatchers.IO) {
            // Fetch directory tree from Hermetic backend API
            api.getDirectoryTree(projectPath.ifEmpty { "/srv/projects/api-service" }, depth = 3, dirsOnly = false)
        }.onSuccess { map ->
            nodes = parseTree(map, 0)
            isLoading = false
        }.onFailure {
            // Fallback to mock structure if API fails
            nodes = listOf(
                ExplorerNode.Directory("api-service", 0, true),
                ExplorerNode.Directory("src", 1, true),
                ExplorerNode.Directory("auth", 2, true),
                ExplorerNode.File("index.ts", 3, "ts"),
                ExplorerNode.File("jwt.ts", 3, "ts"),
                ExplorerNode.File("middleware.ts", 3, "ts"),
                ExplorerNode.Directory("controllers", 2, false),
                ExplorerNode.Directory("services", 2, false),
                ExplorerNode.Directory("routes", 2, false),
                ExplorerNode.Directory("tests", 1, false),
                ExplorerNode.File(".env", 1, "env"),
                ExplorerNode.File("package.json", 1, "json"),
                ExplorerNode.File("README.md", 1, "md"),
            )
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Path Display
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .padding(vertical = 10.dp, horizontal = 12.dp)
        ) {
            Text(
                text = projectPath.ifEmpty { "/srv/projects/api-service" },
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tab Selector (Segmented control)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .padding(2.dp)
        ) {
            val tabs = listOf("Árbol", "Archivos")
            tabs.forEach { tab ->
                val selected = selectedTab == tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { selectedTab = tab }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tab,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp,
                        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            // Tree List / File list
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (selectedTab == "Árbol") {
                    items(nodes) { node ->
                        ExplorerRow(node = node, onNodeClick = { clicked ->
                            if (clicked is ExplorerNode.Directory) {
                                nodes = nodes.map {
                                    if (it is ExplorerNode.Directory && it.name == clicked.name) {
                                        it.copy(isOpen = !it.isOpen)
                                    } else it
                                }
                            }
                        })
                    }
                } else {
                    // Flat file list
                    val files = nodes.filterIsInstance<ExplorerNode.File>()
                    items(files) { file ->
                        ExplorerRow(node = file, onNodeClick = {})
                    }
                }
            }
        }
    }
}

// Recursive parser helper
@Suppress("UNCHECKED_CAST")
private fun parseTree(map: Map<String, Any>, depth: Int): List<ExplorerNode> {
    val name = map["name"] as? String ?: ""
    val isDir = map["is_dir"] as? Boolean ?: (map["isDir"] as? Boolean ?: false)
    val result = mutableListOf<ExplorerNode>()

    if (isDir) {
        result.add(ExplorerNode.Directory(name, depth, isOpen = true))
        val children = map["children"] as? List<Map<String, Any>>
        children?.forEach { child ->
            result.addAll(parseTree(child, depth + 1))
        }
    } else {
        val ext = name.substringAfterLast(".", "")
        result.add(ExplorerNode.File(name, depth, ext))
    }
    return result
}

@Composable
fun ExplorerRow(
    node: ExplorerNode,
    onNodeClick: (ExplorerNode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNodeClick(node) }
            .padding(
                start = (node.depth * 16).dp,
                top = 6.dp,
                bottom = 6.dp,
                end = 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (node) {
            is ExplorerNode.Directory -> {
                Icon(
                    imageVector = if (node.isOpen) Icons.Default.KeyboardArrowDown else Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (node.isOpen) Icons.Default.FolderOpen else Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = node.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            is ExplorerNode.File -> {
                Spacer(modifier = Modifier.width(20.dp)) // indentation offset for file arrow placeholder
                FileExtensionIcon(ext = node.extension)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = node.name,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun FileExtensionIcon(ext: String) {
    val bg = when (ext) {
        "ts" -> Color(0xFFEFF6FF)
        "json" -> Color(0xFFFFF7ED)
        "md" -> Color(0xFFF0FDF4)
        "env" -> Color(0xFFFAF5FF)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textCol = when (ext) {
        "ts" -> Color(0xFF2563EB)
        "json" -> Color(0xFFEA580C)
        "md" -> Color(0xFF16A34A)
        "env" -> Color(0xFF9333EA)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val label = when (ext) {
        "ts" -> "TS"
        "json" -> "{}"
        "md" -> "M↓"
        "env" -> ".env"
        else -> "F"
    }

    Box(
        modifier = Modifier
            .size(width = 30.dp, height = 20.dp)
            .background(bg, RoundedCornerShape(4.dp))
            .border(0.5.dp, textCol.copy(alpha = 0.3f), RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = textCol
        )
    }
}
