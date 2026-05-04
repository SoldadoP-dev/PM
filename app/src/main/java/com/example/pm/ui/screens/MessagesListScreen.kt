package com.example.pm.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.pm.ChatRoom
import com.example.pm.R
import com.example.pm.User
import com.example.pm.ui.components.UserAvatar
import com.example.pm.ui.theme.InstaGradient
import com.example.pm.ui.theme.NeonPink
import com.example.pm.ui.theme.NeonPurple
import com.example.pm.ui.viewmodels.MessagesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesListScreen(
    rootNavController: NavHostController,
    viewModel: MessagesViewModel = hiltViewModel()
) {
    val chats by viewModel.chats.collectAsState()
    val currentUserId = viewModel.currentUserId
    val user by viewModel.currentUser.collectAsState(initial = null)
    val followingUsers by viewModel.followingUsers.collectAsState(initial = emptyList())
    val usersWithStories by viewModel.usersWithStories.collectAsState()
    val unseenStoriesUserIds by viewModel.unseenStoriesUserIds.collectAsState()
    val hasStories by viewModel.currentUserHasStories.collectAsState(initial = false)
    val isUploading by viewModel.isUploading.collectAsState()
    
    var searchQuery by remember { mutableStateOf("") }
    var chatToDelete by remember { mutableStateOf<ChatRoom?>(null) }
    val context = LocalContext.current

    val storyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.uploadStory(it, context.contentResolver.getType(it)?.startsWith("video") == true) }
    }

    if (chatToDelete != null) {
        AlertDialog(
            onDismissRequest = { chatToDelete = null },
            title = { Text(stringResource(R.string.delete_chat_title)) },
            text = { Text(stringResource(R.string.delete_chat_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteChat(chatToDelete!!.id)
                    chatToDelete = null
                }) {
                    Text(stringResource(R.string.delete), color = NeonPink)
                }
            },
            dismissButton = {
                TextButton(onClick = { chatToDelete = null }) {
                    Text(stringResource(R.string.cancel), color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1C1C1C),
            titleContentColor = Color.White,
            textContentColor = Color.Gray
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Cabecera compacta
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = user?.username ?: stringResource(R.string.loading),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }

            // Buscador Dinámico
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(Color(0xFF262626), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
                    Icon(Icons.Default.Search, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(stringResource(R.string.search_hint), color = Color.Gray, fontSize = 14.sp)
                            }
                            innerTextField()
                        }
                    )
                }
            }

            // Carrusel de Historias
            LazyRow(
                modifier = Modifier.padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(75.dp)
                    ) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            val hasUnseen = user?.uid in unseenStoriesUserIds
                            val auraBrush = when {
                                hasUnseen -> InstaGradient
                                hasStories -> Brush.linearGradient(listOf(Color.Gray, Color.Gray))
                                else -> Brush.linearGradient(listOf(Color.DarkGray, Color.DarkGray))
                            }

                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .border(2.5.dp, auraBrush, CircleShape)
                                    .padding(4.dp)
                            ) {
                                UserAvatar(
                                    url = user?.photoUrl, 
                                    username = user?.username ?: "", 
                                    size = 64.dp,
                                    showIndicator = false,
                                    onClick = {
                                        if (hasStories) {
                                            rootNavController.navigate("storyView/${user?.uid}")
                                        } else {
                                            storyLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                                        }
                                    }
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(NeonPurple, CircleShape)
                                    .border(2.dp, Color.Black, CircleShape)
                                    .clickable { 
                                        storyLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Add, null, tint = Color.Black, modifier = Modifier.size(14.dp))
                            }
                        }
                        Text(stringResource(R.string.your_story), color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }

                val usersWithActualStories = followingUsers.filter { usersWithStories.contains(it.uid) }
                
                items(usersWithActualStories) { followedUser ->
                    val hasUnseen = followedUser.uid in unseenStoriesUserIds
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(75.dp).clickable { 
                            rootNavController.navigate("storyView/${followedUser.uid}")
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .border(
                                    2.5.dp, 
                                    if (hasUnseen) InstaGradient else Brush.linearGradient(listOf(Color.Gray, Color.Gray)), 
                                    CircleShape
                                )
                                .padding(4.dp)
                        ) {
                            UserAvatar(
                                url = followedUser.photoUrl, 
                                username = followedUser.username, 
                                size = 64.dp,
                                showIndicator = false 
                            )
                        }
                        Text(
                            text = followedUser.username,
                            color = Color.White,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.messages), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    text = stringResource(R.string.requests_label), 
                    color = NeonPurple,
                    fontWeight = FontWeight.Bold, 
                    fontSize = 14.sp,
                    modifier = Modifier.clickable { rootNavController.navigate("notifications") }
                )
            }

            LazyColumn {
                items(chats) { chat ->
                    EnrichedChatItemWithFilter(chat, currentUserId, rootNavController, viewModel, searchQuery) {
                        chatToDelete = chat
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isUploading,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 60.dp)
        ) {
            Card(
                modifier = Modifier.padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = NeonPurple,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(stringResource(R.string.publishing), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EnrichedChatItemWithFilter(
    chat: ChatRoom, 
    currentUserId: String, 
    rootNavController: NavHostController, 
    viewModel: MessagesViewModel,
    searchQuery: String,
    onLongClick: () -> Unit
) {
    var otherUser by remember { mutableStateOf<User?>(null) }
    var unreadCount by remember { mutableIntStateOf(0) }

    if (chat.isGroup) {
        val groupName = chat.name ?: stringResource(R.string.group_default_name)
        if (searchQuery.isEmpty() || groupName.contains(searchQuery, ignoreCase = true)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            val encodedName = Uri.encode(groupName)
                            rootNavController.navigate("chat/${chat.id}/$encodedName/group")
                        },
                        onLongClick = onLongClick
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(NeonPurple),
                    contentAlignment = Alignment.Center
                ) {
                    if (!chat.photoUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = chat.photoUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(groupName.take(1).uppercase(), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                }
                
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(groupName, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.Groups, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                    }
                    Text(
                        chat.lastMessage, 
                        color = Color.Gray, 
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    } else {
        val otherId = chat.participants.find { it != currentUserId } ?: ""
        LaunchedEffect(otherId) {
            viewModel.getOtherUserInfoFull(otherId) { user ->
                otherUser = user
            }
        }

        LaunchedEffect(chat.id) {
            viewModel.getUnreadCount(chat.id).collect {
                unreadCount = it
            }
        }

        val otherName = otherUser?.username ?: "..."

        if (searchQuery.isEmpty() || otherName.contains(searchQuery, ignoreCase = true)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            val encodedName = Uri.encode(otherName)
                            rootNavController.navigate("chat/${chat.id}/$encodedName/$otherId")
                        },
                        onLongClick = onLongClick
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UserAvatar(
                    url = otherUser?.photoUrl, 
                    username = otherName, 
                    size = 56.dp, 
                    isOnline = otherUser?.isOnline ?: false, 
                    ghostMode = otherUser?.ghostMode ?: false,
                    showIndicator = true 
                )
                
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(otherName, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(
                        chat.lastMessage, 
                        color = if (unreadCount > 0) Color.White else Color.Gray, 
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (unreadCount > 0) FontWeight.Bold else FontWeight.Normal
                    )
                }
                
                if (unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(NeonPurple, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(unreadCount.toString(), color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
