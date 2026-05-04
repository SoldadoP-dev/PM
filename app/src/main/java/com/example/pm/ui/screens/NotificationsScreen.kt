package com.example.pm.ui.screens

import android.net.Uri
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
import androidx.compose.material.icons.outlined.Circle
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NotificationsScreen(
    navController: NavHostController,
    viewModel: NotificationsViewModel = hiltViewModel()
) {
    val requests by viewModel.followRequests.collectAsState()
    val generalNotifs by viewModel.generalNotifications.collectAsState()
    
    var selectedMeetupNotif by remember { mutableStateOf<ActivityNotification?>(null) }
    
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedNotifs = remember { mutableStateListOf<String>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    if (isSelectionMode) {
                        Text(stringResource(R.string.selected_count, selectedNotifs.size), fontWeight = FontWeight.Bold)
                    } else {
                        Text(stringResource(R.string.activity), fontWeight = FontWeight.Bold) 
                    }
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = { 
                            isSelectionMode = false
                            selectedNotifs.clear()
                        }) {
                            Icon(Icons.Default.Close, null, tint = Color.White)
                        }
                    } else {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            val allNotifIds = requests.map { it.id } + generalNotifs.map { it.id }
                            if (selectedNotifs.size == allNotifIds.size) {
                                selectedNotifs.clear()
                            } else {
                                selectedNotifs.clear()
                                selectedNotifs.addAll(allNotifIds)
                            }
                        }) {
                            Icon(Icons.Default.SelectAll, null, tint = Color.White)
                        }
                        
                        if (selectedNotifs.isNotEmpty()) {
                            IconButton(onClick = {
                                viewModel.deleteNotifications(selectedNotifs.toList())
                                selectedNotifs.clear()
                                isSelectionMode = false
                            }) {
                                Icon(Icons.Default.Delete, null, tint = NeonPink)
                            }
                        }
                    } else {
                        TextButton(onClick = { viewModel.markAllAsRead() }) {
                            Text(stringResource(R.string.read_all), color = NeonPurple)
                        }
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
                    SectionHeader(stringResource(R.string.follow_requests_title))
                }
                items(requests, key = { it.id }) { notif ->
                    NotificationItem(
                        notif = notif,
                        isSelectionMode = isSelectionMode,
                        isSelected = selectedNotifs.contains(notif.id),
                        onLongClick = { 
                            isSelectionMode = true
                            selectedNotifs.add(notif.id)
                        },
                        onAccept = { viewModel.acceptFollowRequest(notif) },
                        onDecline = { /* Opcional: implementar rechazo */ },
                        onClick = {
                            if (isSelectionMode) {
                                if (selectedNotifs.contains(notif.id)) selectedNotifs.remove(notif.id)
                                else selectedNotifs.add(notif.id)
                                
                                if (selectedNotifs.isEmpty()) isSelectionMode = false
                            } else {
                                viewModel.markAsRead(notif.id)
                                navController.navigate("otherProfile/${notif.fromUserId}")
                            }
                        }
                    )
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }

            // SECCIÓN DE ACTIVIDAD GENERAL
            item {
                SectionHeader(stringResource(R.string.recent_activity_title))
            }
            
            if (generalNotifs.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.no_activity), color = Color.Gray, fontSize = 14.sp)
                    }
                }
            } else {
                items(generalNotifs, key = { it.id }) { notif ->
                    NotificationItem(
                        notif = notif,
                        isSelectionMode = isSelectionMode,
                        isSelected = selectedNotifs.contains(notif.id),
                        onLongClick = { 
                            isSelectionMode = true
                            selectedNotifs.add(notif.id)
                        },
                        onAccept = { if (notif.type == "meetup_invitation") viewModel.respondToMeetup(notif, true) },
                        onDecline = { if (notif.type == "meetup_invitation") viewModel.respondToMeetup(notif, false) },
                        onClick = {
                            if (isSelectionMode) {
                                if (selectedNotifs.contains(notif.id)) selectedNotifs.remove(notif.id)
                                else selectedNotifs.add(notif.id)
                                
                                if (selectedNotifs.isEmpty()) isSelectionMode = false
                            } else {
                                viewModel.markAsRead(notif.id)
                                when(notif.type) {
                                    "like", "comment", "comment_like" -> navController.navigate("postFeed/single/none/${notif.targetId}")
                                    "message", "venue_invitation" -> {
                                        val encodedName = Uri.encode(notif.fromUsername)
                                        navController.navigate("chat/${notif.targetId}/$encodedName/${notif.fromUserId}")
                                    }
                                    "meetup_invitation" -> {
                                        selectedMeetupNotif = notif
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    selectedMeetupNotif?.let { notif ->
        MeetupPreviewDialog(
            notif = notif,
            onDismiss = { selectedMeetupNotif = null },
            onAccept = { 
                viewModel.respondToMeetup(notif, true)
                selectedMeetupNotif = null
            },
            onDecline = { 
                viewModel.respondToMeetup(notif, false)
                selectedMeetupNotif = null
            }
        )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotificationItem(
    notif: ActivityNotification,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onLongClick: () -> Unit = {},
    onAccept: () -> Unit,
    onDecline: () -> Unit = {},
    onClick: () -> Unit
) {
    var isProcessedLocal by rememberSaveable(notif.id) { mutableStateOf(false) }

    ListItem(
        modifier = Modifier
            .background(
                when {
                    isSelected -> NeonPurple.copy(alpha = 0.2f)
                    !notif.isRead -> NeonPurple.copy(alpha = 0.05f)
                    else -> Color.Transparent
                }
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        headlineContent = { 
            Text(
                text = when(notif.type) {
                    "follow_request" -> stringResource(R.string.notif_follow_request, notif.fromUsername)
                    "like" -> stringResource(R.string.notif_like, notif.fromUsername)
                    "comment" -> stringResource(R.string.notif_comment, notif.fromUsername)
                    "comment_like" -> stringResource(R.string.notif_comment_like, notif.fromUsername)
                    "message" -> stringResource(R.string.notif_message, notif.fromUsername)
                    "meetup_invitation", "venue_invitation" -> stringResource(R.string.notif_meetup, notif.fromUsername)
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
                    "comment", "message", "meetup_invitation", "venue_invitation" -> notif.content
                    "like", "comment_like" -> stringResource(R.string.tap_to_view_post)
                    "follow_request" -> stringResource(R.string.tap_to_view_profile)
                    else -> stringResource(R.string.new_activity_label)
                },
                color = Color.Gray,
                fontSize = 12.sp,
                maxLines = 1
            ) 
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (notif.type == "follow_request") {
                    val currentlyProcessed = isProcessedLocal || notif.isRead
                    
                    if (!currentlyProcessed) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = {
                                    isProcessedLocal = true
                                    onDecline()
                                },
                                modifier = Modifier.size(32.dp).background(Color.DarkGray, CircleShape)
                            ) {
                                Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = {
                                    isProcessedLocal = true
                                    onAccept()
                                },
                                modifier = Modifier.size(32.dp).background(NeonPurple, CircleShape)
                            ) {
                                Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                } else if (notif.type == "meetup_invitation") {
                    if (!notif.isRead) {
                        Button(
                            onClick = onClick,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp),
                            shape = CircleShape
                        ) {
                            Text(stringResource(R.string.view_label), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
                
                if (isSelectionMode) {
                    Icon(
                        if (isSelected) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                        contentDescription = null,
                        tint = if (isSelected) NeonPurple else Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                } else if (!notif.isRead && notif.type != "follow_request" && notif.type != "meetup_invitation") {
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
                        "meetup_invitation", "venue_invitation" -> Icons.Default.Groups
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

@Composable
fun MeetupPreviewDialog(
    notif: ActivityNotification,
    onDismiss: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.meetup_invitation_title), color = Color.White, fontWeight = FontWeight.Bold) },
        text = { 
            Column {
                Text(notif.content, color = Color.LightGray, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.meetup_invitation_desc), color = Color.Gray, fontSize = 13.sp)
            }
        },
        confirmButton = {
            Button(onClick = onAccept, colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)) { 
                Text(stringResource(R.string.accept), color = Color.White, fontWeight = FontWeight.Bold) 
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) { 
                Text(stringResource(R.string.decline), color = Color.Gray)
            }
        },
        containerColor = DeepSpace
    )
}
