package com.example.pm.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.pm.Story
import com.example.pm.ui.components.UserAvatar
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

    val pagerState = rememberPagerState(pageCount = { orderedUserIds.size })

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize().background(Color.Black),
        beyondViewportPageCount = 1
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
                        alpha = lerp(
                            start = 0.5f,
                            stop = 1f,
                            fraction = 1f - pageOffset.coerceIn(0f, 1f)
                        )
                        val scale = lerp(
                            start = 0.85f,
                            stop = 1f,
                            fraction = 1f - pageOffset.coerceIn(0f, 1f)
                        )
                        scaleX = scale
                        scaleY = scale
                    }
            ) {
                UserStoryContent(
                    stories = userStories,
                    isActive = pagerState.currentPage == pageIndex && !pagerState.isScrollInProgress,
                    onStorySeen = { storyId -> 
                        viewModel.markAsSeen(storyId)
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
                    onClose = { navController.popBackStack() }
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
    onAllStoriesFinished: () -> Unit,
    onClose: () -> Unit
) {
    var currentIndex by remember { mutableIntStateOf(0) }
    val currentStory = stories[currentIndex]
    val progress = remember { Animatable(0f) }

    LaunchedEffect(currentIndex, stories, isActive) {
        if (isActive) {
            onStorySeen(currentStory.id)
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 5000, easing = LinearEasing)
            )
            if (currentIndex < stories.size - 1) {
                currentIndex++
            } else {
                onAllStoriesFinished()
            }
        } else {
            progress.snapTo(0f)
        }
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .pointerInput(stories) {
            detectTapGestures { offset ->
                if (offset.x < size.width / 3) {
                    if (currentIndex > 0) currentIndex--
                } else {
                    if (currentIndex < stories.size - 1) currentIndex++ else onAllStoriesFinished()
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

        // Indicadores de progreso corregidos para ser homogéneos y sin puntos blancos
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
                
                // Usamos un Box con fondo gris y otro encima blanco para el progreso
                // Sin redondeos (clip) para evitar el efecto de "punto blanco"
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(2.dp)
                        .background(Color(0xFF555555)) // Gris oscuro homogéneo para el track
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
            UserAvatar(currentStory.userPhotoUrl, currentStory.username, 40.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Text(currentStory.username, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, null, tint = Color.White)
            }
        }
    }
}
