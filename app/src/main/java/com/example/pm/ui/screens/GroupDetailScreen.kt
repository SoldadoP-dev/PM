package com.example.pm.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.pm.User
import com.example.pm.ui.components.UserAvatar
import com.example.pm.ui.theme.DeepSpace
import com.example.pm.ui.theme.NeonPink
import com.example.pm.ui.theme.NeonPurple
import com.example.pm.ui.viewmodels.GroupDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    navController: NavHostController,
    chatId: String,
    viewModel: GroupDetailViewModel = hiltViewModel()
) {
    val chatRoom by viewModel.chatRoom.collectAsState()
    val participants by viewModel.participants.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    
    var showLeaveDialog by remember { mutableStateOf(false) }

    LaunchedEffect(chatId) {
        viewModel.loadGroup(chatId)
    }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text("Abandonar grupo") },
            text = { Text("¿Estás seguro de que quieres salir de esta quedada?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.leaveGroup(chatId) {
                        navController.navigate("main?tab=2") {
                            popUpTo("main") { inclusive = true }
                        }
                    }
                }) {
                    Text("Abandonar", color = NeonPink)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) {
                    Text("Cancelar", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1C1C1C),
            titleContentColor = Color.White,
            textContentColor = Color.Gray
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Info. de la quedada", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = DeepSpace
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                // Imagen del grupo
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(NeonPurple),
                    contentAlignment = Alignment.Center
                ) {
                    if (!chatRoom?.photoUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = chatRoom?.photoUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            chatRoom?.name?.take(1)?.uppercase() ?: "?", 
                            color = Color.Black, 
                            fontSize = 40.sp, 
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    chatRoom?.name ?: "Quedada", 
                    color = Color.White, 
                    fontSize = 22.sp, 
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(32.dp))
                
                Text(
                    "Participantes (${participants.size})", 
                    color = Color.Gray, 
                    fontSize = 14.sp, 
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            items(participants) { user ->
                ParticipantItem(user) {
                    navController.navigate("otherProfile/${user.uid}")
                }
            }

            item {
                Spacer(modifier = Modifier.height(40.dp))
                Button(
                    onClick = { showLeaveDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonPink.copy(alpha = 0.5f))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ExitToApp, null, tint = NeonPink)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Abandonar grupo", color = NeonPink, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun ParticipantItem(user: User, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(user.photoUrl, user.username, 44.dp)
        Spacer(modifier = Modifier.width(16.dp))
        Text(user.username, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}
