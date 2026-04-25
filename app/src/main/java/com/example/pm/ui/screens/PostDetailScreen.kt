package com.example.pm.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
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
import com.example.pm.Comment
import com.example.pm.ui.components.UserAvatar
import com.example.pm.ui.components.VideoPlayer
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
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(postId) {
        viewModel.loadPost(postId)
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar publicación") },
            text = { Text("¿Estás seguro de que quieres eliminar esta publicación? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePost(postId) {
                        showDeleteDialog = false
                        navController.popBackStack()
                    }
                }) {
                    Text("Eliminar", color = NeonPink)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar", color = Color.Gray)
                }
            },
            containerColor = CardGray,
            titleContentColor = Color.White,
            textContentColor = Color.Gray
        )
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
                actions = {
                    if (post?.userId == currentUserId) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = NeonPink)
                        }
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
                    var authorPhoto by remember(p.id) { mutableStateOf(p.userPhotoUrl) }
                    LaunchedEffect(p.userId, p.userPhotoUrl) {
                        if (authorPhoto.isBlank()) {
                            authorPhoto = viewModel.getUserPhoto(p.userId) ?: ""
                        }
                    }

                    Column {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            UserAvatar(authorPhoto, p.username, 32.dp)
                            Text(p.username, modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        
                        Box(modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
                            if (p.videoUrl != null) {
                                VideoPlayer(
                                    videoUrl = p.videoUrl,
                                    modifier = Modifier.fillMaxWidth().height(400.dp)
                                )
                            } else if (p.imageUrl != null) {
                                AsyncImage(
                                    model = p.imageUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxWidth(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }

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
                CommentItem(comment, viewModel)
            }
        }
    }
}

@Composable
fun CommentItem(comment: Comment, viewModel: PostDetailViewModel) {
    var photoUrl by remember(comment.id) { mutableStateOf(comment.userPhotoUrl) }
    
    LaunchedEffect(comment.userId, comment.userPhotoUrl) {
        if (photoUrl.isBlank() && comment.userId.isNotBlank()) {
            val fetchedPhoto = viewModel.getUserPhoto(comment.userId)
            if (fetchedPhoto != null) {
                photoUrl = fetchedPhoto
            }
        }
    }

    ListItem(
        headlineContent = { Text(comment.username, fontWeight = FontWeight.Bold, color = Color.White) },
        supportingContent = { Text(comment.text, color = Color.White) },
        leadingContent = { UserAvatar(photoUrl, comment.username, 32.dp) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
