package com.example.pm.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.pm.ui.components.UserAvatar
import com.example.pm.ui.theme.CardGray
import com.example.pm.ui.theme.DeepSpace
import com.example.pm.ui.theme.NeonPink
import com.example.pm.ui.theme.NeonPurple
import com.example.pm.ui.viewmodels.PostDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    navController: NavHostController,
    postId: String,
    viewModel: PostDetailViewModel = hiltViewModel()
) {
    val post by viewModel.post.collectAsState()
    val comments by viewModel.comments.collectAsState()
    var commentText by remember { mutableStateOf("") }
    val currentUserId = viewModel.currentUserId

    LaunchedEffect(postId) {
        viewModel.loadPost(postId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Publicación", fontWeight = FontWeight.Bold) },
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
                    TextField(
                        value = commentText, onValueChange = { commentText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Añadir comentario...", color = Color.Gray) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = CardGray, 
                            unfocusedContainerColor = CardGray, 
                            focusedIndicatorColor = Color.Transparent, 
                            unfocusedIndicatorColor = Color.Transparent, 
                            focusedTextColor = Color.White, 
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
                    IconButton(onClick = {
                        if (commentText.isNotBlank()) {
                            viewModel.addComment(postId, commentText)
                            commentText = ""
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.Send, null, tint = NeonPurple)
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().background(DeepSpace).padding(padding)) {
            item {
                post?.let { p ->
                    Column {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            UserAvatar(null, p.username, 32.dp)
                            Text(p.username, modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        AsyncImage(
                            model = p.imageUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp),
                            contentScale = ContentScale.Fit
                        )
                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            val isLiked = p.likedBy.contains(currentUserId)
                            IconButton(onClick = { viewModel.toggleLike(postId) }) {
                                Icon(if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = null, tint = if (isLiked) NeonPink else Color.White)
                            }
                            Text("${p.likesCount} likes", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        if (p.caption.isNotEmpty()) {
                            Text(text = p.caption, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = Color.White)
                        }
                        HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            items(comments) { comment ->
                ListItem(
                    headlineContent = { Text(comment.username, fontWeight = FontWeight.Bold, color = Color.White) },
                    supportingContent = { Text(comment.text, color = Color.White) },
                    leadingContent = { UserAvatar(null, comment.username, 32.dp) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}
