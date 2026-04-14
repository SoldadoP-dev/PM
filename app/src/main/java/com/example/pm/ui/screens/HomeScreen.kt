package com.example.pm.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.pm.R
import com.example.pm.Venue
import com.example.pm.ui.components.UserAvatar
import com.example.pm.ui.theme.*
import com.example.pm.ui.viewmodels.HomeViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onVenueClick: (Venue) -> Unit,
    rootNavController: NavHostController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    Column(modifier = Modifier.fillMaxSize().background(DeepSpace)) {
        StoriesRow(viewModel, rootNavController)
        Box(modifier = Modifier.weight(1f)) {
            MapSection(onVenueClick, viewModel)
        }
    }
}

@Composable
fun StoriesRow(viewModel: HomeViewModel, rootNavController: NavHostController) {
    val currentUser by viewModel.currentUser.collectAsState()
    val stories by viewModel.stories.collectAsState()
    val isUploading by viewModel.isUploadingStory.collectAsState()
    val context = LocalContext.current

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { viewModel.uploadStory(it) }
            Toast.makeText(context, context.getString(R.string.story_uploaded), Toast.LENGTH_SHORT).show()
        }
    }

    val groupedStories = stories.groupBy { it.userId }

    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            val myStories = groupedStories[currentUser?.uid] ?: emptyList()
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .border(3.dp, if (myStories.isNotEmpty()) InstaGradient else androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color.Gray, Color.Gray)), CircleShape)
                            .padding(5.dp)
                            .clip(CircleShape)
                            .background(Color.DarkGray)
                            .clickable { 
                                if (myStories.isNotEmpty()) {
                                    rootNavController.navigate("storyView/${currentUser?.uid}")
                                } else {
                                    galleryLauncher.launch("image/*")
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isUploading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = NeonPurple)
                        } else {
                            UserAvatar(currentUser?.photoUrl, currentUser?.username ?: "Tú", 70.dp)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(NeonPurple, CircleShape)
                            .border(2.dp, DeepSpace, CircleShape)
                            .clickable { galleryLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp), tint = Color.Black)
                    }
                }
                Text(stringResource(R.string.your_story), color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        
        items(groupedStories.keys.toList().filter { it != currentUser?.uid }) { userId ->
            val userStories = groupedStories[userId] ?: emptyList()
            if (userStories.isNotEmpty()) {
                val firstStory = userStories.first()
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .border(3.dp, InstaGradient, CircleShape)
                            .padding(5.dp)
                            .clip(CircleShape)
                            .background(Color.DarkGray)
                            .clickable { 
                                rootNavController.navigate("storyView/$userId")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        UserAvatar(firstStory.userPhotoUrl, firstStory.username, 70.dp)
                    }
                    Text(firstStory.username, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun MapSection(onVenueClick: (Venue) -> Unit, viewModel: HomeViewModel) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val venues by viewModel.venues.collectAsState()
    
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(40.4168, -3.7038), 14f)
    }
    
    var searchQuery by remember { mutableStateOf("") }
    var hasPermission by remember { 
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) 
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }

    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val filteredVenues = if (searchQuery.isBlank()) venues 
                         else venues.filter { it.name.contains(searchQuery, ignoreCase = true) || it.category.contains(searchQuery, ignoreCase = true) }

    val sharedPrefs = context.getSharedPreferences("Settings", android.content.Context.MODE_PRIVATE)
    val isDarkMap = sharedPrefs.getBoolean("dark_map", true)
    
    val mapStyleJson = if (isDarkMap) {
        """[{"featureType":"all","elementType":"labels.text.fill","stylers":[{"color":"#ffffff"}]},{"featureType":"all","elementType":"labels.text.stroke","stylers":[{"color":"#000000"},{"lightness":13}]},{"featureType":"administrative","elementType":"geometry.fill","stylers":[{"color":"#000000"}]},{"featureType":"administrative","elementType":"geometry.stroke","stylers":[{"color":"#144b53"},{"lightness":14},{"weight":1.4}]},{"featureType":"landscape","elementType":"all","stylers":[{"color":"#08304b"}]},{"featureType":"poi","elementType":"geometry","stylers":[{"color":"#0c4152"},{"lightness":5}]},{"featureType":"road.highway","elementType":"geometry.fill","stylers":[{"color":"#000000"}]},{"featureType":"road.highway","elementType":"geometry.stroke","stylers":[{"color":"#0b434f"},{"lightness":25}]},{"featureType":"road.arterial","elementType":"geometry.fill","stylers":[{"color":"#000000"}]},{"featureType":"road.arterial","elementType":"geometry.stroke","stylers":[{"color":"#0b3d51"},{"lightness":16}]},{"featureType":"road.local","elementType":"geometry","stylers":[{"color":"#000000"}]},{"featureType":"transit","elementType":"all","stylers":[{"color":"#146474"}]},{"featureType":"water","elementType":"all","stylers":[{"color":"#021019"}]}]"""
    } else null

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = hasPermission,
                mapStyleOptions = mapStyleJson?.let { MapStyleOptions(it) }
            ),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = true)
        ) {
            filteredVenues.forEach { venue ->
                Marker(
                    state = MarkerState(position = LatLng(venue.location.latitude, venue.location.longitude)),
                    title = venue.name,
                    onClick = { 
                        onVenueClick(venue)
                        scope.launch {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngZoom(LatLng(venue.location.latitude, venue.location.longitude), 16f), 1000
                            )
                        }
                        true 
                    }
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .fillMaxWidth()
                .zIndex(5f)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(25.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.8f)),
                border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.5f))
            ) {
                Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, null, tint = NeonPurple)
                    Spacer(modifier = Modifier.width(12.dp))
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(stringResource(R.string.search_venue), color = Color.Gray) },
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                    )
                }
            }

            AnimatedVisibility(visible = searchQuery.isNotBlank()) {
                Card(
                    modifier = Modifier.padding(top = 8.dp).fillMaxWidth().heightIn(max = 200.dp),
                    colors = CardDefaults.cardColors(containerColor = CardGray.copy(alpha = 0.9f))
                ) {
                    LazyColumn {
                        items(filteredVenues) { venue ->
                            ListItem(
                                modifier = Modifier.clickable {
                                    onVenueClick(venue)
                                    searchQuery = ""
                                    focusManager.clearFocus()
                                },
                                headlineContent = { Text(venue.name, color = Color.White) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }
                }
            }
        }
    }
}
