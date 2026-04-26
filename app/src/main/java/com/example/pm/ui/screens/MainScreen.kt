package com.example.pm.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.pm.R
import com.example.pm.Venue
import com.example.pm.ui.components.BottomNavigationBar
import com.example.pm.ui.theme.CardGray
import com.example.pm.ui.theme.DeepSpace
import com.example.pm.ui.theme.NeonPink
import com.example.pm.ui.theme.NeonPurple
import com.example.pm.ui.viewmodels.MainViewModel
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    rootNavController: NavHostController,
    viewModel: MainViewModel = hiltViewModel()
) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 4 })
    val scope = rememberCoroutineScope()
    var selectedVenue by remember { mutableStateOf<Venue?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    
    val unreadNotifications by viewModel.unreadNotificationsCount.collectAsState()
    val hasUnread = unreadNotifications > 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.logo),
                            contentDescription = "PM Logo",
                            modifier = Modifier.size(32.dp).clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.app_name), fontWeight = FontWeight.ExtraBold, color = NeonPurple, fontSize = 24.sp)
                    }
                },
                actions = {
                    if (pagerState.currentPage == 3) {
                        IconButton(onClick = { rootNavController.navigate("settings") }) {
                            Icon(Icons.Default.Settings, null, tint = Color.White)
                        }
                    }
                    
                    IconButton(onClick = { rootNavController.navigate("notifications") }) {
                        BadgedBox(
                            badge = {
                                if (hasUnread) {
                                    Badge(
                                        containerColor = NeonPink,
                                        modifier = Modifier.size(8.dp).offset(x = (-4).dp, y = 4.dp)
                                    )
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (hasUnread) Icons.Default.Favorite else Icons.Default.FavoriteBorder, 
                                contentDescription = "Notifications", 
                                tint = if (hasUnread) NeonPink else Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        bottomBar = { 
            BottomNavigationBar(
                selectedIndex = pagerState.currentPage,
                onItemSelected = { index ->
                    scope.launch { 
                        pagerState.scrollToPage(index)
                    }
                }
            ) 
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).background(DeepSpace)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 0,
                userScrollEnabled = pagerState.currentPage != 0
            ) { page ->
                val isVisible = abs(pagerState.currentPage - page) <= 1
                if (isVisible) {
                    when (page) {
                        0 -> HomeScreen(
                            onVenueClick = { venue ->
                                selectedVenue = venue
                                showBottomSheet = true
                            },
                            rootNavController = rootNavController
                        )
                        1 -> ExploreScreen(rootNavController)
                        2 -> MessagesListScreen(rootNavController)
                        3 -> ProfileScreen(rootNavController)
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize())
                }
            }
            
            if (showBottomSheet && selectedVenue != null) {
                ModalBottomSheet(
                    onDismissRequest = { showBottomSheet = false },
                    containerColor = CardGray,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                ) {
                    VenueDetailSheet(selectedVenue!!, rootNavController)
                }
            }
        }
    }
}
