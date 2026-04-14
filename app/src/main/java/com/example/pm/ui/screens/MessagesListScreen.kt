package com.example.pm.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.pm.ChatRoom
import com.example.pm.R
import com.example.pm.ui.components.UserAvatar
import com.example.pm.ui.theme.DeepSpace
import com.example.pm.ui.theme.NeonPink
import com.example.pm.ui.theme.NeonPurple
import com.example.pm.ui.viewmodels.MessagesViewModel

@Composable
fun MessagesListScreen(
    rootNavController: NavHostController,
    viewModel: MessagesViewModel = hiltViewModel()
) {
    val chats by viewModel.chats.collectAsState()
    val currentUserId = viewModel.currentUserId

    Column(modifier = Modifier.fillMaxSize().background(DeepSpace)) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.messages), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.weight(1f))
            Icon(Icons.Default.Edit, null, tint = NeonPurple)
        }
        LazyColumn {
            items(chats) { chat ->
                ChatItem(chat, currentUserId, rootNavController, viewModel)
            }
        }
    }
}

@Composable
fun ChatItem(chat: ChatRoom, currentUserId: String, rootNavController: NavHostController, viewModel: MessagesViewModel) {
    val otherId = chat.participants.find { it != currentUserId } ?: ""
    var otherName by remember { mutableStateOf("Cargando...") }
    var otherPhoto by remember { mutableStateOf<String?>(null) }
    var unreadCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(otherId) {
        viewModel.getOtherUserInfo(otherId) { name, photo ->
            otherName = name
            otherPhoto = photo
        }
    }

    LaunchedEffect(chat.id) {
        viewModel.getUnreadCount(chat.id).collect {
            unreadCount = it
        }
    }

    ListItem(
        modifier = Modifier.clickable {
            val encodedName = Uri.encode(otherName)
            rootNavController.navigate("chat/${chat.id}/$encodedName/$otherId")
        },
        headlineContent = {
            Text(
                otherName,
                fontWeight = if (unreadCount > 0) FontWeight.ExtraBold else FontWeight.Bold,
                color = Color.White
            )
        },
        supportingContent = {
            Text(
                chat.lastMessage,
                fontWeight = if (unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
                color = if (unreadCount > 0) Color.White else Color.Gray,
                maxLines = 1
            )
        },
        trailingContent = {
            if (unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(NeonPink, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = unreadCount.toString(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        },
        leadingContent = {
            Box(
                modifier = Modifier.size(56.dp).border(2.dp, NeonPurple, CircleShape)
                    .padding(3.dp)
            ) {
                UserAvatar(otherPhoto, otherName, size = 50.dp)
            }
        },
        colors = ListItemDefaults.colors(containerColor = DeepSpace)
    )
}
