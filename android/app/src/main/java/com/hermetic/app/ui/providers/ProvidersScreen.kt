package com.hermetic.app.ui.providers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermetic.app.data.model.Provider
import com.hermetic.app.data.model.ProviderType
import com.hermetic.app.data.remote.HermeticApi
import com.hermetic.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ProvidersScreen(api: HermeticApi) {
    var providers by remember { mutableStateOf<List<Provider>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            api.getProviders()
        }.onSuccess {
            providers = it
            isLoading = false
        }.onFailure {
            isLoading = false
        }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(providers) { provider ->
                ProviderCard(provider = provider)
            }
        }
    }
}

@Composable
fun ProviderCard(provider: Provider) {
    // Style configurations based on provider type
    val (logoChar, logoBg, logoTextCol) = when (provider.providerType) {
        ProviderType.OPENAI -> Triple("O", Color(0xFF10a37f), Color.White)
        ProviderType.ANTHROPIC -> Triple("A", Color(0xFFfcfaf7), Color(0xFFcc785c))
        ProviderType.GEMINI -> Triple("G", Color(0xFFEFF6FF), Color(0xFF1A73E8))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Provider Logo
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(logoBg, CircleShape)
                    .border(1.dp, logoTextCol.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = logoChar,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = logoTextCol
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Provider Info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = provider.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = provider.defaultModel,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Status Badge
            val isDark = androidx.compose.foundation.isSystemInDarkTheme()
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
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = badgeTextCol
                )
            }
        }
    }
}
