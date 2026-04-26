package com.example.pm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.pm.R
import com.example.pm.ui.theme.CardGray
import com.example.pm.ui.theme.NeonPurple

@Composable
fun UserAvatar(
    url: String?, 
    username: String, 
    size: Dp, 
    isOnline: Boolean = false,
    ghostMode: Boolean = false,
    showIndicator: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .size(size)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(CardGray),
            contentAlignment = Alignment.Center
        ) {
            if (!url.isNullOrEmpty()) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Text(username.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.4).sp)
            }
        }

        // INDICADOR ONLINE REALISTA (Funcional)
        if (showIndicator) {
            val statusColor = if (isOnline && !ghostMode) Color(0xFF4CAF50) else Color.Gray
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.22f)
                    .background(statusColor, CircleShape)
                    .border(2.dp, Color.Black, CircleShape)
            )
        }
    }
}

@Composable
fun BottomNavigationBar(selectedIndex: Int, onItemSelected: (Int) -> Unit) {
    val items = listOf(
        Triple(0, Icons.Default.Map, stringResource(R.string.map)),
        Triple(1, Icons.Default.Search, stringResource(R.string.explore)),
        Triple(2, Icons.AutoMirrored.Filled.Chat, stringResource(R.string.chats)),
        Triple(3, Icons.Default.Person, stringResource(R.string.profile))
    )
    NavigationBar(containerColor = Color.Black) {
        items.forEach { (index, icon, label) ->
            NavigationBarItem(
                icon = { Icon(icon, null) },
                label = { Text(label, fontSize = 10.sp) },
                selected = selectedIndex == index,
                onClick = { onItemSelected(index) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = NeonPurple,
                    unselectedIconColor = Color.Gray,
                    indicatorColor = NeonPurple.copy(alpha = 0.1f)
                )
            )
        }
    }
}

@Composable
fun ProfileStat(value: String, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Text(value, fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 20.sp)
        Text(label, color = Color.Gray, fontSize = 13.sp)
    }
}
