package com.example.pm.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.pm.Post
import com.example.pm.R
import com.example.pm.ui.components.ProfileStat
import com.example.pm.ui.theme.*
import com.example.pm.ui.viewmodels.ProfileViewModel

@Composable
fun ProfileScreen(
    rootNavController: NavHostController,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val user by viewModel.user.collectAsState()
    val posts by viewModel.posts.collectAsState()
    val context = LocalContext.current
    var showMediaPicker by remember { mutableStateOf(false) }

    val profileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.updateProfilePicture(it) }
    }

    val mediaLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val type = context.contentResolver.getType(it)
            val isVideo = type?.contains("video") == true
            viewModel.createPost(it, "", isVideo)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(DeepSpace)) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .border(3.dp, InstaGradient, CircleShape)
                        .padding(5.dp)
                        .background(Color.DarkGray, CircleShape)
                        .clickable { profileLauncher.launch("image/*") }, 
                    contentAlignment = Alignment.Center
                ) {
                    if (!user?.photoUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = user?.photoUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(user?.username?.take(1)?.uppercase() ?: "?", fontSize = 40.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(NeonPurple, CircleShape)
                        .border(2.dp, DeepSpace, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp), tint = Color.Black)
                }
            }
            
            Text(user?.username ?: "...", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(top = 12.dp))
            
            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ProfileStat(posts.size.toString(), stringResource(R.string.posts)) {}
                ProfileStat(user?.followersCount?.toString() ?: "0", stringResource(R.string.followers)) { 
                    rootNavController.navigate("userList/followers/${user?.uid}") 
                }
                ProfileStat(user?.followingCount?.toString() ?: "0", stringResource(R.string.following)) { 
                    rootNavController.navigate("userList/following/${user?.uid}") 
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { mediaLauncher.launch("*/*") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
                ) {
                    Icon(Icons.Default.Add, null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.new_photo), color = Color.Black, fontWeight = FontWeight.Bold)
                }
                
                IconButton(
                    onClick = { 
                        viewModel.logout()
                        rootNavController.navigate("login") { popUpTo(0) } 
                    },
                    modifier = Modifier.background(CardGray, RoundedCornerShape(12.dp))
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, null, tint = NeonPink)
                }
            }
        }

        PostGrid(posts) { post ->
            rootNavController.navigate("postDetail/${post.id}")
        }
    }
}

@Composable
fun PostGrid(posts: List<Post>, onPostClick: (Post) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(1.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        items(posts) { post ->
            Box(modifier = Modifier.aspectRatio(1f).clickable { onPostClick(post) }) {
                AsyncImage(
                    model = post.imageUrl ?: post.videoUrl, // Muestra thumbnail si es video (Coil maneja thumbnails de video básicos)
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
    }
}
