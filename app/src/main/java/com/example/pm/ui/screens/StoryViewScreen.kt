package com.example.pm.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.pm.ui.components.UserAvatar
import com.example.pm.ui.theme.NeonPurple
import com.example.pm.ui.viewmodels.StoryViewModel

@Composable
fun StoryViewScreen(
    navController: NavHostController,
    userId: String,
    viewModel: StoryViewModel = hiltViewModel()
) {
    val stories by viewModel.stories.collectAsState()
    var currentIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(userId) {
        viewModel.loadStories(userId)
    }

    if (stories.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = NeonPurple)
        }
        return
    }

    val currentStory = stories[currentIndex]
    val progress = remember(currentIndex) { Animatable(0f) }

    LaunchedEffect(currentIndex) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 15000, easing = LinearEasing)
        )
        if (currentIndex < stories.size - 1) {
            currentIndex++
        } else {
            navController.popBackStack()
        }
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)
        .pointerInput(Unit) {
            detectTapGestures { offset ->
                if (offset.x < size.width / 3) {
                    if (currentIndex > 0) currentIndex-- else navController.popBackStack()
                } else {
                    if (currentIndex < stories.size - 1) currentIndex++ else navController.popBackStack()
                }
            }
        }
    ) {
        AsyncImage(
            model = currentStory.imageUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        // Indicadores de progreso
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            stories.forEachIndexed { index, _ ->
                LinearProgressIndicator(
                    progress = { if (index < currentIndex) 1f else if (index == currentIndex) progress.value else 0f },
                    modifier = Modifier
                        .weight(1f)
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp)),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f),
                )
            }
        }

        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 60.dp, start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatar(currentStory.userPhotoUrl, currentStory.username, 40.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Text(currentStory.username, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.Close, null, tint = Color.White)
            }
        }
    }
}
