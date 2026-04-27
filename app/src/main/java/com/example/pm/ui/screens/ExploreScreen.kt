package com.example.pm.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.pm.Post
import com.example.pm.R
import com.example.pm.User
import com.example.pm.ui.components.UserAvatar
import com.example.pm.ui.theme.CardGray
import com.example.pm.ui.theme.NeonPurple
import com.example.pm.ui.viewmodels.ExploreViewModel

@Composable
fun ExploreScreen(
    navController: NavHostController,
    viewModel: ExploreViewModel = hiltViewModel()
) {
    var searchQueryText by remember { mutableStateOf("") }
    val searchResult by viewModel.searchResults.collectAsState()
    val posts by viewModel.posts.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Buscador estilo Instagram Real
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(Color(0xFF262626), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
                    Icon(Icons.Default.Search, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    BasicTextField(
                        value = searchQueryText,
                        onValueChange = { 
                            searchQueryText = it
                            viewModel.searchUsers(it)
                        },
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                        cursorBrush = SolidColor(Color.White),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            if (searchQueryText.isEmpty()) {
                                Text("Buscar...", color = Color.Gray, fontSize = 16.sp)
                            }
                            innerTextField()
                        }
                    )
                }
            }
        }

        if (searchQueryText.isNotBlank()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), 
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(searchResult, key = { it.uid }) { user ->
                    UserSearchItem(user, currentUser, viewModel, navController)
                }
            }
        } else {
            if (posts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NeonPurple)
                }
            } else {
                // Grid de 3 columnas estilo Explorar Real
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 1.dp),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    items(posts, key = { it.id }) { post ->
                        ExplorePostItem(post, navController)
                    }
                }
            }
        }
    }
}

@Composable
fun ExplorePostItem(post: Post, navController: NavHostController) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable { navController.navigate("postDetail/${post.id}") }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(post.imageUrl ?: post.videoUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        
        // Overlay de información
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f)), startY = 300f))
        )
        
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Favorite, 
                null, 
                tint = Color.White, 
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = post.likesCount.toString(), 
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun UserSearchItem(user: User, currentUser: User?, viewModel: ExploreViewModel, navController: NavHostController) {
    var isOptimisticPending by rememberSaveable(user.uid) { mutableStateOf(false) }
    val isFollowing = currentUser?.followingUids?.contains(user.uid) ?: false
    val isPending = user.pendingFollowRequests.contains(currentUser?.uid ?: "") || isOptimisticPending

    Card(
        modifier = Modifier.fillMaxWidth().clickable { navController.navigate("otherProfile/${user.uid}") },
        colors = CardDefaults.cardColors(containerColor = CardGray),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(user.photoUrl, user.username, size = 45.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                user.username, 
                modifier = Modifier.weight(1f), 
                color = Color.White, 
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Button(
                onClick = { 
                    if (!isFollowing && !isPending) {
                        isOptimisticPending = true
                        viewModel.followUser(user) 
                    }
                },
                enabled = !isFollowing && !isPending,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = when {
                        isFollowing || isPending -> Color.DarkGray
                        else -> Color(0xFF833AB4)
                    },
                    disabledContainerColor = Color.DarkGray,
                    disabledContentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp).widthIn(min = 90.dp)
            ) {
                Text(
                    text = when {
                        isFollowing -> stringResource(R.string.following)
                        isPending -> stringResource(R.string.requested)
                        else -> stringResource(R.string.follow)
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }
    }
}
