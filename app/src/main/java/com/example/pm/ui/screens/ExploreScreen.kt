package com.example.pm.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.pm.Post
import com.example.pm.R
import com.example.pm.User
import com.example.pm.ui.components.UserAvatar
import com.example.pm.ui.theme.CardGray
import com.example.pm.ui.theme.DeepSpace
import com.example.pm.ui.theme.NeonPurple
import com.example.pm.ui.viewmodels.ExploreViewModel

@Composable
fun ExploreScreen(
    navController: NavHostController,
    viewModel: ExploreViewModel = hiltViewModel()
) {
    var searchQuery by remember { mutableStateOf("") }
    val searchResult by viewModel.searchResults.collectAsState()
    val posts by viewModel.posts.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(DeepSpace)) {
        OutlinedTextField(
            value = searchQuery, 
            onValueChange = { 
                searchQuery = it
                viewModel.searchUsers(it)
            },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text(stringResource(R.string.search_friends)) },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeonPurple, 
                unfocusedBorderColor = Color.DarkGray,
                focusedContainerColor = CardGray,
                unfocusedContainerColor = CardGray
            )
        )

        if (searchQuery.isNotBlank()) {
            LazyColumn(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(searchResult) { user ->
                    UserSearchItem(user, currentUser, viewModel, navController)
                }
            }
        } else {
            if (posts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NeonPurple)
                }
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(1.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalItemSpacing = 2.dp
                ) {
                    items(posts) { post ->
                        ExplorePostItem(post, navController)
                    }
                }
            }
        }
    }
}

@Composable
fun ExplorePostItem(post: Post, navController: NavHostController) {
    val randomHeight = remember(post.id) { (150..300).random().dp }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(randomHeight)
            .clickable { navController.navigate("postDetail/${post.id}") }
    ) {
        AsyncImage(
            model = post.imageUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)), startY = 100f)
        ))
        Row(modifier = Modifier.align(Alignment.BottomStart).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(post.userPhotoUrl, post.username, 24.dp)
            Text(post.username, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun UserSearchItem(user: User, currentUser: User?, viewModel: ExploreViewModel, navController: NavHostController) {
    val isFollowing = currentUser?.followingUids?.contains(user.uid) ?: false
    val isPending = user.pendingFollowRequests.contains(currentUser?.uid ?: "")

    Card(
        modifier = Modifier.fillMaxWidth().clickable { navController.navigate("otherProfile/${user.uid}") },
        colors = CardDefaults.cardColors(containerColor = CardGray)
    ) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(user.photoUrl, user.username, size = 45.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Text(user.username, modifier = Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.SemiBold)

            Button(
                onClick = { viewModel.followUser(user) },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = when {
                        isFollowing -> Color.DarkGray
                        isPending -> Color.Gray
                        else -> NeonPurple
                    }
                )
            ) {
                Text(
                    text = when {
                        isFollowing -> stringResource(R.string.following)
                        isPending -> stringResource(R.string.pending)
                        else -> stringResource(R.string.follow)
                    },
                    color = if (isFollowing || isPending) Color.White else Color.Black,
                    fontSize = 12.sp
                )
            }
        }
    }
}
