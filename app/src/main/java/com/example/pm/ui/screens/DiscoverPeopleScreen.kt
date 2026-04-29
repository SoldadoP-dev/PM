package com.example.pm.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.pm.User
import com.example.pm.ui.components.UserAvatar
import com.example.pm.ui.viewmodels.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverPeopleScreen(
    navController: NavHostController,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val recommendedUsers by viewModel.recommendedUsers.collectAsState()
    val pendingRequests by viewModel.pendingRequests.collectAsState()
    val currentUser by viewModel.user.collectAsState()
    val sentRequests by viewModel.sentRequests.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Descubre personas", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Sugerencias
            items(recommendedUsers) { user ->
                val mutualCount = currentUser?.followingUids?.intersect(user.followingUids.toSet())?.size ?: 0
                val isSentLocally = sentRequests.contains(user.uid)
                val isPendingInDb = user.pendingFollowRequests.contains(currentUser?.uid ?: "")
                
                DiscoverUserItem(
                    user = user,
                    mutualCount = mutualCount,
                    isPending = isSentLocally || isPendingInDb,
                    onFollowClick = { viewModel.followUser(user.uid) },
                    onRemoveClick = { viewModel.removeRecommendedUser(user.uid) },
                    onUserClick = { navController.navigate("otherProfile/${user.uid}") }
                )
            }

            // Sección Solicitudes
            if (pendingRequests.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Solicitudes de seguimiento", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }

                items(pendingRequests) { user ->
                    FollowRequestItem(
                        user = user,
                        onConfirm = { viewModel.handleFollowRequest(user.uid, true) },
                        onDelete = { viewModel.handleFollowRequest(user.uid, false) },
                        onUserClick = { navController.navigate("otherProfile/${user.uid}") }
                    )
                }
            }
        }
    }
}

@Composable
fun DiscoverUserItem(
    user: User,
    mutualCount: Int,
    isPending: Boolean,
    onFollowClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onUserClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onUserClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(user.photoUrl, user.username, 60.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(user.username, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            val mutualText = when {
                mutualCount == 0 -> "Sin amigos en comun"
                mutualCount == 1 -> "1 amigo en común"
                else -> "$mutualCount amigos en común"
            }
            Text(
                mutualText, 
                color = Color.Gray, 
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Button(
            onClick = { if (!isPending) onFollowClick() },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isPending) Color(0xFF262626) else Color(0xFF833AB4)
            ),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
            modifier = Modifier.height(32.dp)
        ) {
            Text(if (isPending) "Solicitado" else "Seguir", color = if (isPending) Color.Gray else Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        IconButton(onClick = onRemoveClick) {
            Icon(Icons.Default.Close, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun FollowRequestItem(
    user: User,
    onConfirm: () -> Unit,
    onDelete: () -> Unit,
    onUserClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onUserClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(user.photoUrl, user.username, 60.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            user.username, 
            color = Color.White, 
            fontWeight = FontWeight.Bold, 
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF833AB4)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("Confirmar", color = Color.White, fontSize = 13.sp)
            }
            Button(
                onClick = onDelete,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF262626)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("Eliminar", color = Color.White, fontSize = 13.sp)
            }
        }
    }
}
