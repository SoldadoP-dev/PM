package com.example.pm.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.pm.ActivityNotification
import com.example.pm.R
import com.example.pm.ui.theme.CardGray
import com.example.pm.ui.theme.DeepSpace
import com.example.pm.ui.theme.NeonPink
import com.example.pm.ui.theme.NeonPurple
import com.example.pm.ui.viewmodels.NotificationsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    navController: NavHostController,
    viewModel: NotificationsViewModel = hiltViewModel()
) {
    val requests by viewModel.followRequests.collectAsState()
    val generalNotifs by viewModel.generalNotifications.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.activity), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.markAllAsRead() }) {
                        Text(stringResource(R.string.read_all), color = NeonPurple)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepSpace)
                .padding(padding)
        ) {
            // SECCIÓN DE SOLICITUDES
            if (requests.isNotEmpty()) {
                item {
                    SectionHeader("Solicitudes de seguimiento")
                }
                items(requests, key = { it.id }) { notif ->
                    NotificationItem(
                        notif = notif,
                        onAccept = { viewModel.acceptFollowRequest(notif) },
                        onClick = {
                            viewModel.markAsRead(notif.id)
                            navController.navigate("otherProfile/${notif.fromUserId}")
                        }
                    )
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }

            // SECCIÓN DE ACTIVIDAD GENERAL
            item {
                SectionHeader("Actividad reciente")
            }
            
            if (generalNotifs.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No hay actividad nueva", color = Color.Gray, fontSize = 14.sp)
                    }
                }
            } else {
                items(generalNotifs, key = { it.id }) { notif ->
                    NotificationItem(
                        notif = notif,
                        onAccept = {},
                        onClick = {
                            viewModel.markAsRead(notif.id)
                            when(notif.type) {
                                "like", "comment", "comment_like" -> navController.navigate("postDetail/${notif.targetId}")
                                "message" -> navController.navigate("chat/${notif.targetId}/${notif.fromUsername}/${notif.fromUserId}")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
fun NotificationItem(notif: ActivityNotification, onAccept: () -> Unit, onClick: () -> Unit) {
    var isAcceptedLocal by rememberSaveable(notif.id) { mutableStateOf(false) }
    
    ListItem(
        modifier = Modifier
            .background(if (!notif.isRead) NeonPurple.copy(alpha = 0.05f) else Color.Transparent)
            .clickable { onClick() },
        headlineContent = { 
            Text(
                text = when(notif.type) {
                    "follow_request" -> "${notif.fromUsername} quiere seguirte"
                    "like" -> "${notif.fromUsername} le dio like a tu post"
                    "comment" -> "${notif.fromUsername} comentó tu post"
                    "comment_like" -> "A ${notif.fromUsername} le gusta tu respuesta"
                    "message" -> "${notif.fromUsername} te envió un mensaje"
                    else -> notif.fromUsername
                },
                fontWeight = if (!notif.isRead) FontWeight.ExtraBold else FontWeight.Normal, 
                color = Color.White,
                fontSize = 14.sp
            ) 
        },
        supportingContent = { 
            Text(
                text = when(notif.type) {
                    "comment", "message" -> notif.content
                    "like", "comment_like" -> "Toca para ver la publicación"
                    "follow_request" -> "Toca para ver el perfil"
                    else -> "Nueva actividad"
                },
                color = Color.Gray,
                fontSize = 12.sp,
                maxLines = 1
            ) 
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (notif.type == "follow_request") {
                    val currentlyAccepted = isAcceptedLocal || notif.isRead
                    
                    Button(
                        onClick = {
                            isAcceptedLocal = true
                            onAccept()
                        }, 
                        enabled = !currentlyAccepted,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentlyAccepted) Color.DarkGray else NeonPurple,
                            disabledContainerColor = Color.DarkGray,
                            disabledContentColor = Color.White
                        ), 
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp).widthIn(min = 84.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = if (currentlyAccepted) stringResource(R.string.accepted) else stringResource(R.string.accept),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
                if (!notif.isRead) {
                    Box(modifier = Modifier.size(10.dp).background(NeonPink, CircleShape))
                }
            }
        },
        leadingContent = { 
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(CardGray, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when(notif.type) {
                        "like", "comment_like" -> Icons.Default.Favorite
                        "comment" -> Icons.AutoMirrored.Filled.Comment
                        "message" -> Icons.AutoMirrored.Filled.Chat
                        "follow_request" -> Icons.Default.PersonAdd
                        else -> Icons.Default.Notifications
                    },
                    contentDescription = null,
                    tint = if (notif.type == "like" || notif.type == "comment_like") NeonPink else NeonPurple,
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
