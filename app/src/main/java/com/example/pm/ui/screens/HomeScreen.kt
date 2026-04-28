package com.example.pm.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.filled.LocationOn
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import android.graphics.drawable.BitmapDrawable
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
    val seenStories by viewModel.seenStories.collectAsState()
    val isUploading by viewModel.isUploadingStory.collectAsState()
    val context = LocalContext.current

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri -> 
                val isVideo = context.contentResolver.getType(uri)?.startsWith("video") == true
                viewModel.uploadStory(uri, isVideo) 
            }
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
            val allSeen = myStories.isNotEmpty() && myStories.all { seenStories.contains(it.id) }
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .border(
                                3.dp, 
                                if (myStories.isEmpty()) Brush.linearGradient(listOf(Color.DarkGray, Color.DarkGray))
                                else if (allSeen) Brush.linearGradient(listOf(Color.Gray, Color.Gray))
                                else InstaGradient, 
                                CircleShape
                            )
                            .padding(5.dp)
                            .clip(CircleShape)
                            .background(Color.DarkGray)
                            .clickable { 
                                if (myStories.isNotEmpty()) {
                                    rootNavController.navigate("storyView/${currentUser?.uid}")
                                } else {
                                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
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
                            .clickable { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
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
                val allSeen = userStories.all { seenStories.contains(it.id) }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .border(
                                3.dp, 
                                if (allSeen) Brush.linearGradient(listOf(Color.Gray, Color.Gray)) else InstaGradient, 
                                CircleShape
                            )
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
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    
    var isDarkMap by remember { mutableStateOf(sharedPrefs.getBoolean("dark_map", systemDark)) }
    
    DisposableEffect(sharedPrefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == "dark_map") {
                isDarkMap = prefs.getBoolean("dark_map", systemDark)
            }
        }
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    
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
                VenueMarker(
                    venue = venue,
                    onVenueClick = onVenueClick,
                    cameraPositionState = cameraPositionState,
                    scope = scope
                )
            }
        }

        if (!hasPermission) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 64.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = NeonPink.copy(alpha = 0.9f))
            ) {
                Text(
                    text = "Habilita la localización para disfrutar de las recomendaciones cercanas.",
                    color = Color.White,
                    modifier = Modifier.padding(16.dp),
                    fontSize = 14.sp
                )
            }
        }

        // Buscador coherente con ExploreScreen (40dp de altura, 10dp de radio)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .fillMaxWidth()
                .zIndex(5f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(Color(0xFF262626), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
                    Icon(Icons.Default.Search, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                        cursorBrush = SolidColor(Color.White),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(stringResource(R.string.search_venue), color = Color.Gray, fontSize = 16.sp)
                            }
                            innerTextField()
                        }
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

@Composable
fun VenueMarker(
    venue: Venue,
    onVenueClick: (Venue) -> Unit,
    cameraPositionState: CameraPositionState,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val imageToLoad = if (venue.logoUrl.isNotEmpty()) venue.logoUrl else venue.photoUrl
    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val context = LocalContext.current

    LaunchedEffect(imageToLoad) {
        if (imageToLoad.isNotEmpty()) {
            val request = ImageRequest.Builder(context)
                .data(imageToLoad)
                .allowHardware(false) // Imprescindible para el mapa de Google
                .build()
            val result = context.imageLoader.execute(request)
            if (result is SuccessResult) {
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    imageBitmap = bitmap.asImageBitmap()
                }
            }
        }
    }

    val markerState = rememberMarkerState(position = LatLng(venue.location.latitude, venue.location.longitude))
    MarkerComposable(
        state = markerState,
        title = venue.name,
        keys = arrayOf(venue.id, imageBitmap ?: "loading"),
        onClick = { 
            onVenueClick(venue)
            scope.launch {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(LatLng(venue.location.latitude, venue.location.longitude), 16f), 1000
                )
            }
            true 
        }
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(2.dp, NeonPurple, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (imageBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = imageBitmap!!,
                    contentDescription = venue.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            } else {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = NeonPink,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

