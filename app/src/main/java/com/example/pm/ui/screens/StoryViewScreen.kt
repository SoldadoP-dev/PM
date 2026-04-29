package com.example.pm.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.example.pm.Story
import com.example.pm.ui.components.UserAvatar
import com.example.pm.ui.components.VideoPlayer
import com.example.pm.ui.theme.NeonPurple
import com.example.pm.ui.viewmodels.StoryViewModel
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@Composable
fun StoryViewScreen(
    navController: NavHostController,
    userId: String,
    viewModel: StoryViewModel = hiltViewModel()
) {
    val userStoriesMap by viewModel.userStoriesMap.collectAsState()
    val orderedUserIds by viewModel.orderedUserIds.collectAsState()
    val currentUserId = viewModel.currentUserId
    val scope = rememberCoroutineScope()

    LaunchedEffect(userId) {
        viewModel.loadAllStories(userId)
    }

    if (orderedUserIds.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = NeonPurple)
        }
        return
    }

    val initialPage = remember(orderedUserIds, userId) {
        val index = orderedUserIds.indexOf(userId)
        if (index != -1) index else 0
    }
    
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { orderedUserIds.size }
    )

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize().background(Color.Black),
        beyondViewportPageCount = 1 // Optimización: precarga el siguiente usuario
    ) { pageIndex ->
        val currentStoryUserId = orderedUserIds[pageIndex]
        val userStories = userStoriesMap[currentStoryUserId] ?: emptyList()
        
        val pageOffset = (
            (pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction
        ).absoluteValue

        if (userStories.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Sincronización de visibilidad para evitar bordes: 0f si no es la página actual o adyacente en scroll
                        alpha = if (pageOffset >= 1f) 0f else lerp(
                            start = 0f, 
                            stop = 1f,
                            fraction = 1f - pageOffset.coerceIn(0f, 1f)
                        )
                    }
            ) {
                UserStoryContent(
                    stories = userStories,
                    isActive = pagerState.currentPage == pageIndex,
                    onStorySeen = { storyId -> 
                        viewModel.markAsSeen(storyId)
                    },
                    onPreviousUser = {
                        if (pageIndex > 0) {
                            scope.launch {
                                pagerState.animateScrollToPage(pageIndex - 1)
                            }
                        }
                    },
                    onAllStoriesFinished = {
                        if (pageIndex < orderedUserIds.size - 1) {
                            scope.launch {
                                pagerState.animateScrollToPage(pageIndex + 1)
                            }
                        } else {
                            navController.popBackStack()
                        }
                    },
                    onClose = { navController.popBackStack() },
                    onUserClick = { targetUserId ->
                        if (targetUserId == currentUserId) {
                            // Redirigir a la pestaña de perfil (3) en la pantalla principal
                            navController.navigate("main?tab=3") {
                                // Limpiamos el stack para no acumular MainScreens
                                popUpTo("main") { inclusive = true }
                            }
                        } else {
                            navController.navigate("otherProfile/$targetUserId")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun UserStoryContent(
    stories: List<Story>,
    isActive: Boolean,
    onStorySeen: (String) -> Unit,
    onPreviousUser: () -> Unit,
    onAllStoriesFinished: () -> Unit,
    onClose: () -> Unit,
    onUserClick: (String) -> Unit
) {
    var currentIndex by remember { mutableIntStateOf(0) }
    val currentStory = stories[currentIndex]
    val progress = remember { Animatable(0f) }
    var isPaused by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Resetear progreso y marcar como vista al cambiar de historia
    LaunchedEffect(currentIndex) {
        onStorySeen(stories[currentIndex].id)
        progress.snapTo(0f)
    }

    // Precarga de la siguiente historia (imagen)
    LaunchedEffect(currentIndex, stories) {
        if (currentIndex < stories.size - 1) {
            val nextStory = stories[currentIndex + 1]
            if (nextStory.videoUrl.isNullOrEmpty()) {
                val request = ImageRequest.Builder(context)
                    .data(nextStory.imageUrl)
                    .build()
                context.imageLoader.enqueue(request)
            }
        }
    }

    // Controlar el progreso de la historia (pausa y reproducción)
    LaunchedEffect(currentIndex, isActive, isPaused) {
        if (isActive && !isPaused) {
            val remaining = 1f - progress.value
            val duration = (5000 * remaining).toInt()
            
            if (duration > 0) {
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = duration, easing = LinearEasing)
                )
            }
            
            if (progress.value >= 1f) {
                if (currentIndex < stories.size - 1) {
                    currentIndex++
                } else {
                    onAllStoriesFinished()
                }
            }
        } else {
            progress.stop()
        }
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .pointerInput(stories) {
            detectTapGestures(
                onPress = { offset ->
                    val startTime = System.currentTimeMillis()
                    isPaused = true
                    val released = tryAwaitRelease()
                    isPaused = false
                    
                    // Solo si fue un toque rápido (menos de 300ms) cambiamos de historia
                    if (released && System.currentTimeMillis() - startTime < 300) {
                        if (offset.x < size.width / 3) {
                            if (currentIndex > 0) currentIndex-- else onPreviousUser()
                        } else {
                            if (currentIndex < stories.size - 1) currentIndex++ else onAllStoriesFinished()
                        }
                    }
                }
            )
        }
    ) {
        if (!currentStory.videoUrl.isNullOrEmpty()) {
            VideoPlayer(
                videoUrl = currentStory.videoUrl,
                modifier = Modifier.fillMaxSize(),
                isPlaying = isActive && !isPaused,
                showControlsOnTap = false
            )
        } else {
            AsyncImage(
                model = currentStory.imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentScale = ContentScale.Fit
            )
        }

        // Indicadores de progreso
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            stories.forEachIndexed { index, _ ->
                val currentProgress = when {
                    index < currentIndex -> 1f
                    index == currentIndex -> progress.value
                    else -> 0f
                }
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(2.dp)
                        .background(Color(0xFF555555))
                ) {
                    if (currentProgress > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(currentProgress)
                                .fillMaxHeight()
                                .background(Color.White)
                        )
                    }
                }
            }
        }

        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 60.dp, start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatar(
                url = currentStory.userPhotoUrl, 
                username = currentStory.username, 
                size = 40.dp,
                onClick = { onUserClick(currentStory.userId) }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = currentStory.username, 
                color = Color.White, 
                fontWeight = FontWeight.Bold, 
                fontSize = 16.sp,
                modifier = Modifier.clickable { onUserClick(currentStory.userId) }
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, null, tint = Color.White)
            }
        }
    }
}
