package com.example.pm.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.pm.Message
import com.example.pm.ui.components.UserAvatar
import com.example.pm.ui.theme.CardGray
import com.example.pm.ui.theme.DeepSpace
import com.example.pm.ui.theme.NeonPurple
import com.example.pm.ui.viewmodels.ChatViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    navController: NavHostController,
    chatId: String,
    otherName: String,
    otherId: String,
    viewModel: ChatViewModel = hiltViewModel()
) {
    var text by remember { mutableStateOf("") }
    val messages by viewModel.messages.collectAsState()
    val otherUser by viewModel.otherUser.collectAsState()
    val chatRoom by viewModel.chatRoom.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    LaunchedEffect(chatId) {
        viewModel.loadChat(chatId, otherId)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    LaunchedEffect(text) {
        if (text.isNotEmpty()) {
            viewModel.setTyping(chatId, true)
            delay(3000)
            viewModel.setTyping(chatId, false)
        } else {
            viewModel.setTyping(chatId, false)
        }
    }

    val isOtherTyping = chatRoom?.typingUsers?.get(otherId) == true
    val isUploadingMedia by viewModel.isUploadingMedia.collectAsState()

    val mediaLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val type = context.contentResolver.getType(it)
            val isVideo = type?.contains("video") == true
            viewModel.sendMedia(chatId, it, isVideo)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(otherUser?.photoUrl, otherName, 36.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(otherName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            if (isOtherTyping) {
                                Text("escribiendo...", fontSize = 12.sp, color = NeonPurple)
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        bottomBar = {
            Surface(color = Color.Black, modifier = Modifier.navigationBarsPadding().imePadding()) {
                Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { mediaLauncher.launch("*/*") }) {
                        Icon(Icons.Default.AddCircleOutline, null, tint = Color.White)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(CardGray, RoundedCornerShape(24.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        if (text.isEmpty()) {
                            Text("Enviar mensaje...", color = Color.Gray)
                        }
                        BasicTextField(
                            value = text,
                            onValueChange = { text = it },
                            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                            cursorBrush = SolidColor(NeonPurple),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    AnimatedVisibility(visible = text.isNotEmpty()) {
                        IconButton(onClick = {
                            if (text.isNotBlank()) {
                                viewModel.sendMessage(chatId, text)
                                text = ""
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.Send, null, tint = NeonPurple, modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().background(DeepSpace).padding(padding)) {
            items(messages) { msg ->
                val isMe = msg.senderId == viewModel.currentUserId
                ChatBubble(msg, isMe)
            }
            if (isUploadingMedia) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.CenterEnd) {
                        CircularProgressIndicator(color = NeonPurple, modifier = Modifier.size(24.dp))
                    }
                }
            }
            if (isOtherTyping) {
                item {
                    TypingIndicator()
                }
            }
        }
    }
}

@Composable
fun ChatBubble(msg: Message, isMe: Boolean) {
    val isMedia = !msg.imageUrl.isNullOrEmpty() || !msg.videoUrl.isNullOrEmpty()
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp), 
        contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        if (isMedia) {
            Box(
                modifier = Modifier
                    .sizeIn(maxWidth = 250.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                if (!msg.imageUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = msg.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.FillWidth
                    )
                }
                if (!msg.videoUrl.isNullOrEmpty()) {
                    Box(
                        modifier = Modifier.aspectRatio(1f).background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PlayCircle, null, tint = Color.White, modifier = Modifier.size(48.dp))
                    }
                }
            }
        } else {
            Surface(
                color = if (isMe) NeonPurple else CardGray,
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = msg.text,
                    color = if (isMe) Color.Black else Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
fun TypingIndicator() {
    Row(
        modifier = Modifier.padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(3) { index ->
            val infiniteTransition = rememberInfiniteTransition(label = "typing")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = index * 200),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "alpha"
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(Color.Gray.copy(alpha = alpha), CircleShape)
            )
        }
    }
}
