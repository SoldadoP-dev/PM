package com.example.pm.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.pm.Comment
import com.example.pm.ui.components.UserAvatar
import com.example.pm.ui.components.VideoPlayer
import com.example.pm.ui.theme.NeonPink
import com.example.pm.ui.viewmodels.PostDetailViewModel

import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.ExperimentalFoundationApi

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PostDetailScreen(
    navController: NavHostController,
    postId: String,
    contextStr: String = "single",
    userId: String = "none",
    viewModel: PostDetailViewModel = hiltViewModel()
) {
    val feed by viewModel.feed.collectAsState()
    val initialPage by viewModel.initialPage.collectAsState()

    LaunchedEffect(postId, contextStr, userId) {
        viewModel.loadFeed(contextStr, userId, postId)
    }

    if (initialPage != -1 && feed.isNotEmpty()) {
        val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { feed.size })

        LaunchedEffect(pagerState.currentPage, feed) {
            if (pagerState.currentPage < feed.size) {
                viewModel.onPageChanged(feed[pagerState.currentPage])
            }
        }

        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { feed[it].id }
        ) { page ->
            val pagePost = feed[page]
            val currentVisiblePost by viewModel.post.collectAsState()
            val displayPost = if (pagerState.currentPage == page) currentVisiblePost ?: pagePost else pagePost

            PostDetailContent(
                post = displayPost,
                viewModel = viewModel,
                navController = navController
            )
        }
    } else {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = NeonPink)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailContent(
    post: com.example.pm.Post,
    viewModel: PostDetailViewModel,
    navController: NavHostController
) {
    val comments by viewModel.comments.collectAsState()
    val firstLiker by viewModel.firstLiker.collectAsState()
    val currentUserId = viewModel.currentUserId
    val currentUser by viewModel.currentUser.collectAsState(initial = null)
    
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // Estados para Sheets
    var showDeleteDialog by remember { mutableStateOf(false) }
    var commentText by remember { mutableStateOf("") }
    var showCommentsSheet by remember { mutableStateOf(false) }
    
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar publicación") },
            text = { Text("¿Estás seguro de que quieres eliminar esta publicación?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePost(post.id) {
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
            containerColor = Color(0xFF1C1C1C),
            titleContentColor = Color.White,
            textContentColor = Color.Gray
        )
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(post.userPhotoUrl, post.username, 32.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(post.username, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                },
                actions = {
                    if (post.userId == currentUserId) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.MoreVert, null, tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    post.let { p ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (p.caption.isNotEmpty()) {
                                Text(
                                    text = p.caption,
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }

                            Box(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp).background(Color.Black), contentAlignment = Alignment.Center) {
                                if (p.videoUrl != null) {
                                    VideoPlayer(videoUrl = p.videoUrl, modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp))
                                } else {
                                    SubcomposeAsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(p.imageUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                                        contentScale = ContentScale.Fit,
                                        loading = { Box(Modifier.fillMaxWidth().aspectRatio(1f).background(Color.DarkGray)) }
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val isLiked = p.likedBy.contains(currentUserId)
                                IconButton(onClick = { viewModel.toggleLike(post.id) }) {
                                    Icon(
                                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = null,
                                        tint = if (isLiked) NeonPink else Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Text("${p.likesCount}", color = Color.White, fontWeight = FontWeight.Bold)

                                Spacer(modifier = Modifier.width(16.dp))

                                IconButton(onClick = { showCommentsSheet = true }) {
                                    Icon(Icons.Outlined.ChatBubbleOutline, null, tint = Color.White, modifier = Modifier.size(26.dp))
                                }
                                Text("${comments.size}", color = Color.White, fontWeight = FontWeight.Bold)
                            }

                            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                if (p.likedBy.isNotEmpty() && firstLiker != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        UserAvatar(firstLiker?.photoUrl, firstLiker?.username ?: "", 20.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = buildAnnotatedString {
                                                append("Le gusta a ")
                                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(firstLiker?.username ?: "") }
                                                if (p.likesCount > 1) {
                                                    append(" y ")
                                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("${p.likesCount - 1} personas más") }
                                                }
                                            },
                                            color = Color.White,
                                            fontSize = 13.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = buildAnnotatedString {
                                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("${p.username} ") }
                                        append(p.caption)
                                    },
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                                Text("Hace poco", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                            HorizontalDivider(color = Color(0xFF262626), modifier = Modifier.padding(top = 16.dp))
                        }
                    }
                }
            }
        }

        // Modal de Comentarios
        if (showCommentsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showCommentsSheet = false },
                sheetState = sheetState,
                containerColor = Color(0xFF121212),
                contentColor = Color.White,
                dragHandle = { BottomSheetDefaults.DragHandle(color = Color.Gray) }
            ) {
                Column(modifier = Modifier.fillMaxHeight(0.9f).padding(bottom = 16.dp)) {
                    Text(
                        "Comentarios",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.5f))
                    
                    Box(modifier = Modifier.weight(1f)) {
                        if (comments.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Todavía no hay comentarios", color = Color.Gray)
                            }
                        } else {
                            LazyColumn {
                                items(comments) { comment ->
                                    CommentItemDetail(comment, viewModel)
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UserAvatar(currentUser?.photoUrl, currentUser?.username ?: "", 32.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        TextField(
                            value = commentText,
                            onValueChange = { commentText = it },
                            modifier = Modifier.weight(1f).focusRequester(focusRequester),
                            placeholder = { Text("Añadir un comentario para ${post.username}...", color = Color.Gray, fontSize = 14.sp) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true
                        )
                        if (commentText.isNotBlank()) {
                            TextButton(onClick = {
                                viewModel.addComment(post.id, commentText)
                                commentText = ""
                                focusManager.clearFocus()
                            }) {
                                Text("Publicar", color = Color(0xFF833AB4), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CommentItemDetail(comment: Comment, viewModel: PostDetailViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(comment.userPhotoUrl, comment.username, 32.dp)
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("${comment.username} ") }
                    withStyle(SpanStyle(color = Color.Gray, fontSize = 12.sp)) { append("7 h") }
                },
                color = Color.White,
                fontSize = 14.sp
            )
            Text(text = comment.text, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
            Text("Responder", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
        
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val isLiked = comment.likedBy.contains(viewModel.currentUserId)
            IconButton(
                onClick = { viewModel.toggleCommentLike(comment.id) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = if (isLiked) NeonPink else Color.Gray,
                    modifier = Modifier.size(22.dp)
                )
            }
            if (comment.likesCount > 0) {
                Text(
                    text = comment.likesCount.toString(),
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
