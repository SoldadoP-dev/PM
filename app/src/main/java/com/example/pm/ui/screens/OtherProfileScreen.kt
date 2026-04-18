package com.example.pm.ui.screens

import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.pm.R
import com.example.pm.ui.components.ProfileStat
import com.example.pm.ui.components.UserAvatar
import com.example.pm.ui.theme.DeepSpace
import com.example.pm.ui.theme.NeonPurple
import com.example.pm.ui.viewmodels.OtherProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtherProfileScreen(
    navController: NavHostController,
    userId: String,
    viewModel: OtherProfileViewModel = hiltViewModel()
) {
    val user by viewModel.user.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val posts by viewModel.posts.collectAsState()
    
    // Estado optimista: se activa al hacer clic y persiste hasta que el servidor confirme
    var isOptimisticRequested by rememberSaveable(userId) { mutableStateOf(false) }

    LaunchedEffect(userId) {
        viewModel.loadProfile(userId)
    }

    val isFollowing = currentUser?.followingUids?.contains(userId) == true
    val isPending = user?.pendingFollowRequests?.contains(currentUser?.uid ?: "") == true

    // Sincronizamos el estado optimista con la realidad cuando los datos llegan
    if (isPending) isOptimisticRequested = false

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(user?.username ?: "", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().background(DeepSpace).padding(padding)) {
            if (user == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NeonPurple)
                }
            } else {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    UserAvatar(user?.photoUrl, user?.username ?: "", size = 100.dp)
                    Text(user?.username ?: "", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
                    
                    Row(modifier = Modifier.padding(vertical = 24.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        ProfileStat(posts.size.toString(), stringResource(R.string.posts)) {}
                        ProfileStat(user?.followersCount?.toString() ?: "0", stringResource(R.string.followers)) {}
                        ProfileStat(user?.followingCount?.toString() ?: "0", stringResource(R.string.following)) {}
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        val currentlyRequested = isPending || isOptimisticRequested
                        
                        Button(
                            onClick = { 
                                if (!isFollowing && !currentlyRequested) {
                                    isOptimisticRequested = true
                                    viewModel.toggleFollow(userId) 
                                }
                            },
                            enabled = !isFollowing && !currentlyRequested,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isFollowing || currentlyRequested) Color.DarkGray else NeonPurple,
                                disabledContainerColor = Color.DarkGray,
                                disabledContentColor = Color.White
                            )
                        ) {
                            Text(
                                text = when {
                                    isFollowing -> stringResource(R.string.following)
                                    currentlyRequested -> stringResource(R.string.requested)
                                    else -> stringResource(R.string.follow)
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Button(
                            onClick = {
                                viewModel.getOrCreateChat(userId) { chatId ->
                                    val encodedName = Uri.encode(user?.username ?: "Usuario")
                                    navController.navigate("chat/$chatId/$encodedName/$userId")
                                }
                            }, 
                            modifier = Modifier.weight(1f), 
                            shape = RoundedCornerShape(12.dp), 
                            colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
                        ) { 
                            Text(stringResource(R.string.message), color = Color.Black, fontWeight = FontWeight.Bold) 
                        }
                    }
                }

                PostGrid(posts) { post ->
                    navController.navigate("postDetail/${post.id}")
                }
            }
        }
    }
}
