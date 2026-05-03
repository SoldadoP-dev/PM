package com.example.pm.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.pm.Post
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
    
    var isOptimisticRequested by rememberSaveable(userId) { mutableStateOf(false) }

    LaunchedEffect(userId) {
        viewModel.loadProfile(userId)
    }

    val isFollowing = currentUser?.followingUids?.contains(userId) == true
    val isPending = user?.pendingFollowRequests?.contains(currentUser?.uid ?: "") == true
    val isPrivate = user?.isPrivate ?: false
    val canSeeContent = !isPrivate || isFollowing || userId == currentUser?.uid

    if (isPending) isOptimisticRequested = false

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(user?.username ?: "", fontWeight = FontWeight.Bold, color = Color.White) },
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
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            UserAvatar(
                                url = user?.photoUrl, 
                                username = user?.username ?: "", 
                                size = 100.dp,
                                isOnline = user?.isOnline ?: false,
                                ghostMode = user?.ghostMode ?: false,
                                showIndicator = true
                            )
                            
                            Text(user?.username ?: "", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
                            
                            Row(modifier = Modifier.padding(vertical = 24.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                ProfileStat(if (canSeeContent) posts.size.toString() else "-", stringResource(R.string.posts)) {}
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
                    }

                    if (canSeeContent) {
                        items(posts, key = { it.id }) { post ->
                            Box(modifier = Modifier.aspectRatio(1f).clickable { 
                                navController.navigate("postFeed/profile/${post.userId}/${post.id}")
                            }) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(post.imageUrl ?: post.videoUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                if (post.videoUrl != null) {
                                    Icon(
                                        Icons.Default.PlayCircle,
                                        null,
                                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp),
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    } else {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 64.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = Color.Gray
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "Esta cuenta es privada",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                Text(
                                    "Síguela para ver sus fotos y vídeos.",
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 32.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
