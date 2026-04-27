package com.example.pm.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.pm.R
import com.example.pm.User
import com.example.pm.ui.components.ProfileStat
import com.example.pm.ui.components.UserAvatar
import com.example.pm.ui.theme.*
import com.example.pm.ui.viewmodels.ProfileViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    rootNavController: NavHostController,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val user by viewModel.user.collectAsState()
    val posts by viewModel.posts.collectAsState()
    val recommendedUsers by viewModel.recommendedUsers.collectAsState()
    val hasStories by viewModel.hasActiveStories.collectAsState(initial = false)
    val isUploading by viewModel.isUploading.collectAsState()
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            val type = context.contentResolver.getType(it)
            val isVideo = type?.startsWith("video") == true
            viewModel.createPost(it, "", isVideo)
        }
    }

    val pagerState = rememberPagerState(pageCount = { 2 })

    if (user == null) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = NeonPurple)
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                // CABECERA PEGADA
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 4.dp, start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Text(
                        user?.username ?: "", 
                        color = Color.White, 
                        fontWeight = FontWeight.Bold, 
                        fontSize = 20.sp
                    )
                }

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Foto con Aura de Historia y Botones de Estado Esquinados
                                Box(contentAlignment = Alignment.BottomEnd) {
                                    Box(
                                        modifier = Modifier
                                            .size(86.dp)
                                            .border(
                                                2.5.dp, 
                                                if (hasStories) InstaGradient else SolidColor(Color.DarkGray), 
                                                CircleShape
                                            )
                                            .padding(4.dp)
                                    ) {
                                        UserAvatar(
                                            url = user?.photoUrl, 
                                            username = user?.username ?: "", 
                                            size = 78.dp,
                                            onClick = {
                                                if (hasStories) {
                                                    rootNavController.navigate("storyView/${user?.uid}")
                                                } else {
                                                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                                                }
                                            }
                                        )
                                    }

                                    // INDICADOR ONLINE
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .offset(x = 6.dp, y = 6.dp)
                                            .size(15.dp)
                                            .background(
                                                if (user?.ghostMode == true) Color.Gray else Color(0xFF4CAF50), 
                                                CircleShape
                                            )
                                            .border(2.dp, Color.Black, CircleShape)
                                    )

                                    // SÍMBOLO MÁS
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .background(Color.White, CircleShape)
                                            .border(2.5.dp, Color.Black, CircleShape)
                                            .clickable { 
                                                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp), tint = Color.Black)
                                    }
                                }

                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    ProfileStat(posts.size.toString(), "publicaciones") {}
                                    ProfileStat(user?.followersCount?.toString() ?: "0", "seguidores") { 
                                        rootNavController.navigate("userList/followers/${user?.uid}") 
                                    }
                                    ProfileStat(user?.followingCount?.toString() ?: "0", "seguidos") { 
                                        rootNavController.navigate("userList/following/${user?.uid}") 
                                    }
                                }
                            }

                            Text(
                                text = user?.username ?: "", 
                                fontWeight = FontWeight.Bold, 
                                color = Color.White, 
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            
                            if (!user?.bio.isNullOrEmpty()) {
                                Text(
                                    text = user!!.bio,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }

                            // BOTONES
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = { rootNavController.navigate("editProfile") },
                                    modifier = Modifier.weight(1f).height(32.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF262626)),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("Editar perfil", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Button(
                                    onClick = { 
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, "Mira mi perfil en PM")
                                            putExtra(Intent.EXTRA_TEXT, "¡Hola! Sígueme en PM: https://pm.app/user/${user?.username}")
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Compartir perfil via"))
                                    },
                                    modifier = Modifier.weight(1f).height(32.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF262626)),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("Compartir", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Button(
                                    onClick = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                                    modifier = Modifier.height(32.dp).widthIn(min = 32.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF262626)),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Add, 
                                        null, 
                                        tint = Color.White, 
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // SECCIÓN RECOMENDADOS
                            if (recommendedUsers.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Descubre personas", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        "Ver todo", 
                                        color = Color(0xFF833AB4),
                                        fontWeight = FontWeight.Bold, 
                                        fontSize = 14.sp,
                                        modifier = Modifier.clickable { 
                                            rootNavController.navigate("discoverPeople")
                                        }
                                    )
                                }
                                LazyRow(
                                    modifier = Modifier.padding(top = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(recommendedUsers) { recUser ->
                                        val mutualCount = user?.followingUids?.intersect(recUser.followingUids.toSet())?.size ?: 0
                                        SuggestedUserCard(
                                            user = recUser, 
                                            mutualCount = mutualCount,
                                            onRemoveClick = { viewModel.removeRecommendedUser(recUser.uid) },
                                            onFollowClick = { viewModel.followUser(recUser.uid) },
                                            onUserClick = { rootNavController.navigate("otherProfile/${recUser.uid}") }
                                        )
                                    }
                                }
                            }

                            // PESTAÑAS
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                IconButton(onClick = { scope.launch { pagerState.animateScrollToPage(0) } }) {
                                    Icon(
                                        Icons.Default.GridView, 
                                        null, 
                                        tint = if (pagerState.currentPage == 0) Color.White else Color.Gray,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                IconButton(onClick = { scope.launch { pagerState.animateScrollToPage(1) } }) {
                                    Icon(
                                        Icons.Default.PlayArrow, 
                                        null, 
                                        tint = if (pagerState.currentPage == 1) Color.White else Color.Gray,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                        }
                    }

                    // CONTENIDO DE PUBLICACIONES (Swipeable entre Fotos y Videos)
                    item {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 500.dp, max = 3000.dp),
                            verticalAlignment = Alignment.Top
                        ) { page ->
                            if (page == 0) {
                                val photoPosts = posts.filter { it.videoUrl == null }
                                if (photoPosts.isEmpty()) {
                                    ProfileEmptyState("No hay fotos aún", galleryLauncher)
                                } else {
                                    ProfilePostsGrid(photoPosts, rootNavController)
                                }
                            } else {
                                val videoPosts = posts.filter { it.videoUrl != null }
                                if (videoPosts.isEmpty()) {
                                    ProfileEmptyState("Todavía no hay vídeos", galleryLauncher)
                                } else {
                                    ProfilePostsGrid(videoPosts, rootNavController)
                                }
                            }
                        }
                    }
                }
            }

            // ANIMACIÓN DE PUBLICANDO
            AnimatedVisibility(
                visible = isUploading,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically(),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 60.dp)
            ) {
                Card(
                    modifier = Modifier.padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color(0xFF833AB4),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Publicando...", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ProfilePostsGrid(posts: List<com.example.pm.Post>, navController: NavHostController) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2), 
        modifier = Modifier.heightIn(max = 3000.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        userScrollEnabled = false 
    ) {
        items(posts) { post ->
            AsyncImage(
                model = post.imageUrl,
                contentDescription = null,
                modifier = Modifier.aspectRatio(1f).clickable { 
                    navController.navigate("postDetail/${post.id}")
                },
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
fun ProfileEmptyState(text: String, launcher: androidx.activity.result.ActivityResultLauncher<PickVisualMediaRequest>) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Publicar", color = Color.White)
        }
    }
}

@Composable
fun SuggestedUserCard(
    user: User, 
    mutualCount: Int,
    onRemoveClick: () -> Unit, 
    onFollowClick: () -> Unit,
    onUserClick: () -> Unit
) {
    Card(
        modifier = Modifier.width(150.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.DarkGray)
    ) {
        Box {
            IconButton(
                onClick = onRemoveClick,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp)
            ) {
                Icon(Icons.Default.Close, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
            }
            Column(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                UserAvatar(
                    url = user.photoUrl, 
                    username = user.username, 
                    size = 60.dp,
                    onClick = onUserClick
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = user.username, 
                    color = Color.White, 
                    fontWeight = FontWeight.Bold, 
                    fontSize = 13.sp, 
                    maxLines = 1,
                    modifier = Modifier.clickable { onUserClick() }
                )
                val mutualText = when {
                    mutualCount == 0 -> "Sin amigos en comun"
                    mutualCount == 1 -> "1 amigo en común"
                    else -> "$mutualCount amigos en común"
                }
                Text(
                    mutualText, 
                    color = Color.Gray, 
                    fontSize = 12.sp, 
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onFollowClick,
                    modifier = Modifier.fillMaxWidth().height(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF833AB4)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Seguir", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}
