package com.example.pm

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.example.pm.ui.theme.PMTheme
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.messaging.FirebaseMessaging
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.text.contains

class MainActivity : ComponentActivity() {
    private val repository = FirebaseRepository()
    private var notificationListener: ListenerRegistration? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Las notificaciones están desactivadas", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        checkAndRequestNotificationPermission()
        updateFcmToken()

        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            val uid = auth.currentUser?.uid
            if (uid != null) {
                startNotificationMonitor(uid)
            } else {
                notificationListener?.remove()
                notificationListener = null
            }
        }

        setContent {
            PMTheme(darkTheme = true) {
                val navController = rememberNavController()
                val context = LocalContext.current
                
                LaunchedEffect(Unit) {
                    val intent = (context as? MainActivity)?.intent
                    intent?.let { handleNotificationIntent(it, navController) }
                }

                AppNavigation(repository, navController)
            }
        }
    }

    private fun handleNotificationIntent(intent: Intent, navController: NavHostController) {
        val targetId = intent.getStringExtra("targetId")
        val type = intent.getStringExtra("type")
        val fromUserId = intent.getStringExtra("fromUserId")
        val fromUsername = intent.getStringExtra("fromUsername") ?: "Usuario"
        
        if (targetId != null && type != null) {
            when (type) {
                "message" -> {
                    if (fromUserId != null) {
                        val encodedName = Uri.encode(fromUsername)
                        navController.navigate("chat/$targetId/$encodedName/$fromUserId")
                    }
                }
                "like", "comment" -> {
                    navController.navigate("postDetail/$targetId")
                }
                "follow_request" -> {
                    navController.navigate("notifications")
                }
            }
            intent.removeExtra("targetId")
            intent.removeExtra("type")
            intent.removeExtra("fromUserId")
            intent.removeExtra("fromUsername")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun startNotificationMonitor(uid: String) {
        if (notificationListener != null) return

        val firestore = FirebaseFirestore.getInstance()

        notificationListener = firestore.collection("notifications")
            .whereEqualTo("toUserId", uid)
            .whereEqualTo("isRead", false)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null || snapshot.isEmpty) return@addSnapshotListener
                
                snapshot.documentChanges.forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val notif = change.document.toObject(ActivityNotification::class.java)
                        
                        if (notif.fromUserId != uid) {
                            val title = when(notif.type) {
                                "message" -> "Nuevo mensaje de ${notif.fromUsername}"
                                "like" -> "Nuevo Like"
                                "comment" -> "Nuevo Comentario"
                                "follow_request" -> "Solicitud de seguimiento"
                                else -> "Notificación de PM"
                            }
                            val message = when(notif.type) {
                                "message", "comment" -> notif.content
                                "like" -> "${notif.fromUsername} le dio like a tu post"
                                "follow_request" -> "${notif.fromUsername} quiere seguirte"
                                else -> "Tienes una nueva actividad"
                            }
                            
                            NotificationHelper.showNotification(
                                this,
                                title,
                                message,
                                notif.targetId,
                                notif.type,
                                notif.fromUserId,
                                notif.fromUsername
                            )
                        }
                    }
                }
            }
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun updateFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                val uid = FirebaseAuth.getInstance().currentUser?.uid
                if (uid != null) {
                    FirebaseFirestore.getInstance().collection("users").document(uid)
                        .update("fcmToken", token)
                }
            }
        }
    }
}

// --- COLORES PREMIUM ---
val NeonPurple = Color(0xFFBB86FC)
val NeonPink = Color(0xFFFF4081)
val DeepSpace = Color(0xFF0A0A0A)
val CardGray = Color(0xFF1A1A1A)
val InstaGradient = Brush.linearGradient(listOf(Color(0xFF833AB4), Color(0xFFFD1D1D), Color(0xFFFCAF45)))


@Composable
fun AppNavigation(repository: FirebaseRepository, navController: NavHostController) {
    val auth = FirebaseAuth.getInstance()
    val startDestination = if (auth.currentUser == null) "login" else "main"

    NavHost(
        navController = navController, 
        startDestination = startDestination,
        enterTransition = { fadeIn(tween(400)) },
        exitTransition = { fadeOut(tween(400)) }
    ) {
        composable("login") { LoginScreen(navController) }
        composable("register") { RegisterScreen(navController) }
        composable("main") { MainScreen(navController, repository) }
        composable("notifications") { NotificationsScreen(navController) }
        
        composable("userList/{type}/{userId}") { backStackEntry ->
            val type = backStackEntry.arguments?.getString("type") ?: ""
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            UserListScreen(navController, type, userId)
        }
        
        composable("chat/{chatId}/{otherName}/{otherId}") { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId") ?: ""
            val otherName = Uri.decode(backStackEntry.arguments?.getString("otherName") ?: "Usuario")
            val otherId = backStackEntry.arguments?.getString("otherId") ?: ""
            ChatDetailScreen(navController, chatId, otherName, otherId)
        }
        
        composable("otherProfile/{userId}") { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            OtherProfileScreen(navController, userId, repository)
        }
        
        composable("storyView/{userId}") { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            StoryViewScreen(navController, userId, repository)
        }

        composable("postDetail/{postId}") { backStackEntry ->
            val postId = backStackEntry.arguments?.getString("postId") ?: ""
            PostDetailScreen(navController, postId, repository)
        }
        
        composable("settings") { SettingsScreen(navController) }
    }
}

@Composable
fun StoryViewScreen(navController: NavHostController, userId: String, repository: FirebaseRepository) {
    val stories by repository.getStoriesByUser(userId).collectAsState(initial = emptyList())
    var currentIndex by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

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

@Composable
fun UserAvatar(url: String?, username: String, size: Dp, onClick: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(CardGray)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (!url.isNullOrEmpty()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(username.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.4).sp)
        }
    }
}

@Composable
fun LoginScreen(navController: NavHostController) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val auth = FirebaseAuth.getInstance()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().background(DeepSpace).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = "App Logo",
            modifier = Modifier.size(120.dp).clip(CircleShape).border(2.dp, NeonPurple, CircleShape)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("PM", fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, color = NeonPurple)
        Text("Tu noche empieza aquí", color = Color.Gray, modifier = Modifier.padding(bottom = 48.dp))

        OutlinedTextField(
            value = email, onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonPurple, unfocusedBorderColor = Color.DarkGray)
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("Contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonPurple, unfocusedBorderColor = Color.DarkGray)
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = {
                scope.launch {
                    try {
                        auth.signInWithEmailAndPassword(email, password).await()
                        navController.navigate("main") { popUpTo("login") { inclusive = true } }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
        ) {
            Text("Entrar", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        TextButton(onClick = { navController.navigate("register") }) {
            Text("¿No tienes cuenta? Regístrate", color = NeonPink)
        }
    }
}

@Composable
fun RegisterScreen(navController: NavHostController) {
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().background(DeepSpace).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = "App Logo",
            modifier = Modifier.size(80.dp).clip(CircleShape).border(2.dp, NeonPurple, CircleShape)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Únete a la Noche", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = NeonPurple)
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedTextField(
            value = username, onValueChange = { username = it },
            label = { Text("Nombre de usuario") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonPurple, unfocusedBorderColor = Color.DarkGray)
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = email, onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonPurple, unfocusedBorderColor = Color.DarkGray)
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("Contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonPurple, unfocusedBorderColor = Color.DarkGray)
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = {
                scope.launch {
                    try {
                        val result = auth.createUserWithEmailAndPassword(email, password).await()
                        val user = User(uid = result.user!!.uid, username = username.lowercase())
                        firestore.collection("users").document(user.uid).set(user).await()
                        navController.navigate("main") { popUpTo("login") { inclusive = true } }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
        ) {
            Text("Crear Cuenta", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(rootNavController: NavHostController, repository: FirebaseRepository) {
    // 0: Map/Home, 1: Explore (Search+Feed), 2: Chats, 3: Profile
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 4 })
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()
    var selectedVenue by remember { mutableStateOf<Venue?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var isGhostMode by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            var unreadNotifications by remember { mutableIntStateOf(0) }
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
            LaunchedEffect(currentUserId) {
                if (currentUserId != null) {
                    FirebaseFirestore.getInstance().collection("notifications")
                        .whereEqualTo("toUserId", currentUserId)
                        .whereEqualTo("isRead", false)
                        .addSnapshotListener { snapshot, _ ->
                            unreadNotifications = snapshot?.size() ?: 0
                        }
                }
            }

            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.logo),
                            contentDescription = "PM Logo",
                            modifier = Modifier.size(32.dp).clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("PM", fontWeight = FontWeight.ExtraBold, color = NeonPurple, fontSize = 24.sp)
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
                                if (unreadNotifications > 0) {
                                    Badge(containerColor = NeonPink) {
                                        Text(unreadNotifications.toString(), color = Color.White)
                                    }
                                }
                            }
                        ) {
                            Icon(if (unreadNotifications > 0) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (unreadNotifications > 0) NeonPink else Color.White)
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
                        pagerState.animateScrollToPage(index) 
                    }
                }
            ) 
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                userScrollEnabled = false // Deshabilitar deslizamiento entre pestañas para evitar conflictos con el mapa
            ) { page ->
                when (page) {
                    0 -> HomeScreen(
                        onVenueClick = { venue ->
                            selectedVenue = venue
                            showBottomSheet = true
                        },
                        repository = repository,
                        rootNavController = rootNavController
                    )
                    1 -> ExploreScreen(repository, rootNavController)
                    2 -> MessagesListScreen(rootNavController)
                    3 -> ProfileScreen(isGhostMode, { isGhostMode = it }, rootNavController, repository)
                }
            }
            
            if (showBottomSheet && selectedVenue != null) {
                ModalBottomSheet(
                    onDismissRequest = { showBottomSheet = false },
                    sheetState = sheetState,
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

@Composable
fun StoryUploadScreen(repository: FirebaseRepository, onComplete: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isUploading by remember { mutableStateOf(false) }
    
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                isUploading = true
                try {
                    uris.forEach { uri ->
                        repository.uploadStory(uri)
                    }
                    Toast.makeText(context, "Historias subidas", Toast.LENGTH_SHORT).show()
                    onComplete()
                } catch (e: Exception) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isUploading = false
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        if (isUploading) {
            CircularProgressIndicator(color = NeonPurple)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CameraAlt, null, tint = Color.White, modifier = Modifier.size(80.dp).clickable {
                    galleryLauncher.launch("image/*")
                })
                Spacer(modifier = Modifier.height(16.dp))
                Text("Desliza a la derecha para el mapa", color = Color.Gray)
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = { galleryLauncher.launch("image/*") },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
                ) {
                    Text("Seleccionar de Galería", color = Color.Black)
                }
            }
        }
    }
}

@Composable
fun BottomNavigationBar(selectedIndex: Int, onItemSelected: (Int) -> Unit) {
    val items = listOf(
        Triple(0, Icons.Default.Map, "Mapa"),
        Triple(1, Icons.Default.Search, "Explorar"),
        Triple(2, Icons.AutoMirrored.Filled.Chat, "Chats"),
        Triple(3, Icons.Default.Person, "Perfil")
    )
    NavigationBar(containerColor = Color.Black) {
        items.forEach { (index, icon, label) ->
            NavigationBarItem(
                icon = { Icon(icon, null) },
                label = { Text(label, fontSize = 10.sp) },
                selected = selectedIndex == index,
                onClick = { onItemSelected(index) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = NeonPurple,
                    unselectedIconColor = Color.Gray,
                    indicatorColor = NeonPurple.copy(alpha = 0.1f)
                )
            )
        }
    }
}

@Composable
fun HomeScreen(onVenueClick: (Venue) -> Unit, repository: FirebaseRepository, rootNavController: NavHostController) {
    Column(modifier = Modifier.fillMaxSize().background(DeepSpace)) {
        StoriesRow(repository, rootNavController)
        Box(modifier = Modifier.weight(1f)) {
            MapSection(onVenueClick)
        }
    }
}

@Composable
fun ExploreScreen(repository: FirebaseRepository, rootNavController: NavHostController) {
    var searchQuery by remember { mutableStateOf("") }
    var searchResult by remember { mutableStateOf<List<User>>(emptyList()) }
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val firestore = FirebaseFirestore.getInstance()
    var currentUser by remember { mutableStateOf<User?>(null) }
    
    val posts by repository.getGlobalPosts().collectAsState(initial = emptyList())

    LaunchedEffect(currentUserId) {
        firestore.collection("users").document(currentUserId)
            .addSnapshotListener { snapshot, _ ->
                currentUser = snapshot?.toObject(User::class.java)
            }
    }

    Column(modifier = Modifier.fillMaxSize().background(DeepSpace)) {
        // Search Bar
        OutlinedTextField(
            value = searchQuery, 
            onValueChange = { 
                searchQuery = it
                if (searchQuery.isNotBlank()) {
                    firestore.collection("users")
                        .whereGreaterThanOrEqualTo("username", searchQuery.lowercase())
                        .whereLessThanOrEqualTo("username", searchQuery.lowercase() + "\uf8ff")
                        .get()
                        .addOnSuccessListener { result ->
                            searchResult = result.toObjects(User::class.java).filter { it.uid != currentUserId }
                        }
                } else {
                    searchResult = emptyList()
                }
            },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("Buscar amigos...") },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeonPurple, 
                unfocusedBorderColor = Color.DarkGray,
                focusedContainerColor = CardGray,
                unfocusedContainerColor = CardGray
            )
        )

        if (searchQuery.isNotBlank()) {
            // User search results
            LazyColumn(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(searchResult) { user ->
                    UserSearchItem(user, currentUser, rootNavController)
                }
            }
        } else {
            // Explore Feed Grid (Instagram style)
            if (posts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NeonPurple)
                }
            } else {
                LazyVerticalStaggeredGrid(
                    columns = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(1.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalItemSpacing = 2.dp
                ) {
                    items(posts) { post ->
                        ExplorePostItem(post, rootNavController)
                    }
                }
            }
        }
    }
}

@Composable
fun ExplorePostItem(post: Post, navController: NavHostController) {
    // Generate an irregular height (random seeded by post.id so it stays stable)
    val randomHeight = remember(post.id) { (150..300).random().dp }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(randomHeight)
            .clickable { navController.navigate("postDetail/${post.id}") }
    ) {
        AsyncImage(
            model = post.imageUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        // Pequeño indicador del autor si se desea, o solo foto
        Box(modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)), startY = 100f)
        ))
        Row(modifier = Modifier.align(Alignment.BottomStart).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(null, post.username, 20.dp)
            Text(post.username, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun StoriesRow(repository: FirebaseRepository, rootNavController: NavHostController) {
    var currentUser by remember { mutableStateOf<User?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isUploading by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                isUploading = true
                try {
                    uris.forEach { uri ->
                        repository.uploadStory(uri)
                    }
                    Toast.makeText(context, "Historia subida", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isUploading = false
                }
            }
        }
    }
    
    LaunchedEffect(Unit) {
        currentUser = repository.getCurrentUser()
    }
    
    val stories by repository.getStories(
        if (currentUser != null) {
            val list = mutableListOf(currentUser!!.uid)
            list.addAll(currentUser!!.followingUids)
            list
        } else emptyList()
    ).collectAsState(initial = emptyList())
    val groupedStories = stories.groupBy { it.userId }

    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- MI HISTORIA ---
        item {
            val myStories = groupedStories[currentUser?.uid] ?: emptyList()
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .border(3.dp, if (myStories.isNotEmpty()) InstaGradient else Brush.linearGradient(listOf(Color.Gray, Color.Gray)), CircleShape)
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
                Text("Tu historia", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        
        // --- HISTORIAS DE OTROS ---
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
fun MapSection(onVenueClick: (Venue) -> Unit) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val firestore = FirebaseFirestore.getInstance()
    
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(40.4168, -3.7038), 14f)
    }
    
    var venues by remember { mutableStateOf<List<Venue>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var hasPermission by remember { 
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) 
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }

    LaunchedEffect(Unit) {
        val collectionNames = listOf(" venues", "venues", "Venues")
        
        collectionNames.forEach { colName ->
            firestore.collection(colName).addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || snapshot.isEmpty) return@addSnapshotListener
                
                venues = snapshot.documents.mapNotNull { doc ->
                    try {
                        Venue(
                            id = doc.id,
                            name = doc.getString("name") ?: doc.getString("Name") ?: "Sin nombre",
                            location = doc.getGeoPoint("location") ?: com.google.firebase.firestore.GeoPoint(40.41, -3.70),
                            address = doc.getString("address") ?: doc.getString("Address") ?: "Madrid",
                            rating = (doc.get("rating") ?: doc.get("Rating") ?: 0.0).toString().toDoubleOrNull() ?: 0.0,
                            category = doc.getString("Category") ?: doc.getString("category") ?: "Club"
                        )
                    } catch (e: Exception) { null }
                }
            }
        }

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
                mapStyleOptions = mapStyleJson?.let { com.google.android.gms.maps.model.MapStyleOptions(it) }
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

        // --- BUSCADOR ---
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
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonPurple.copy(alpha = 0.5f))
            ) {
                Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, null, tint = NeonPurple)
                    Spacer(modifier = Modifier.width(12.dp))
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Buscar discoteca...", color = Color.Gray) },
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



@Composable
fun UserSearchItem(user: User, currentUser: User?, rootNavController: NavHostController) {
    val firestore = FirebaseFirestore.getInstance()
    val currentUserId = currentUser?.uid ?: return
    val scope = rememberCoroutineScope()

    val isFollowing = currentUser.followingUids.contains(user.uid)
    val isPending = user.pendingFollowRequests.contains(currentUserId)

    Card(
        modifier = Modifier.fillMaxWidth().clickable { rootNavController.navigate("otherProfile/${user.uid}") },
        colors = CardDefaults.cardColors(containerColor = CardGray)
    ) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(user.photoUrl, user.username, size = 45.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Text(user.username, modifier = Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.SemiBold)

            Button(
                onClick = {
                    scope.launch {
                        if (!isFollowing && !isPending) {
                            firestore.collection("users").document(user.uid)
                                .update("pendingFollowRequests", FieldValue.arrayUnion(currentUserId))

                            val notif = ActivityNotification(
                                fromUserId = currentUserId,
                                fromUsername = currentUser.username,
                                toUserId = user.uid,
                                type = "follow_request"
                            )
                            firestore.collection("notifications").add(notif)
                        }
                    }
                },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = when {
                        isFollowing -> Color.DarkGray
                        isPending -> Color.Gray
                        else -> NeonPurple
                    }
                )
            ) {
                Text(
                    text = when {
                        isFollowing -> "Siguiendo"
                        isPending -> "Pendiente"
                        else -> "Seguir"
                    },
                    color = if (isFollowing || isPending) Color.White else Color.Black,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(navController: NavHostController) {
    val firestore = FirebaseFirestore.getInstance()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    var notifications by remember { mutableStateOf<List<ActivityNotification>>(emptyList()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(currentUserId) {
        firestore.collection("notifications")
            .whereEqualTo("toUserId", currentUserId)
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.toObjects(ActivityNotification::class.java) ?: emptyList()
                // Sort client-side to avoid needing a Firestore composite index
                notifications = list.sortedByDescending { it.timestamp }.take(50)
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Actividad", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                },
                actions = {
                    TextButton(onClick = {
                        scope.launch {
                            val unreadDocs = firestore.collection("notifications")
                                .whereEqualTo("toUserId", currentUserId)
                                .whereEqualTo("isRead", false)
                                .get().await()
                            for (doc in unreadDocs.documents) {
                                doc.reference.update("isRead", true)
                            }
                        }
                    }) {
                        Text("Leer todas", color = NeonPurple)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().background(DeepSpace).padding(padding)) {
            items(notifications) { notif ->
                NotificationItem(
                    notif = notif,
                    onAccept = {
                        scope.launch {
                            firestore.collection("users").document(currentUserId).update(
                                "followerUids", FieldValue.arrayUnion(notif.fromUserId),
                                "followersCount", FieldValue.increment(1),
                                "pendingFollowRequests", FieldValue.arrayRemove(notif.fromUserId)
                            )
                            firestore.collection("users").document(notif.fromUserId).update(
                                "followingUids", FieldValue.arrayUnion(currentUserId),
                                "followingCount", FieldValue.increment(1)
                            )
                            firestore.collection("notifications").document(notif.id).update("isRead", true)
                        }
                    },
                    onClick = {
                        firestore.collection("notifications").document(notif.id).update("isRead", true)
                        when(notif.type) {
                            "like", "comment" -> navController.navigate("postDetail/${notif.targetId}")
                            "message" -> {
                                navController.navigate("chat/${notif.targetId}/${notif.fromUsername}/${notif.fromUserId}")
                            }
                            "follow_request" -> navController.navigate("otherProfile/${notif.fromUserId}")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun NotificationItem(notif: ActivityNotification, onAccept: () -> Unit, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier
            .background(if (!notif.isRead) NeonPurple.copy(alpha = 0.05f) else Color.Transparent)
            .clickable { onClick() },
        headlineContent = { 
            Text(
                text = when(notif.type) {
                    "follow_request" -> "${notif.fromUsername} quiere seguirte"
                    "like" -> "${notif.fromUsername} le dio like a tu post"
                    "comment" -> "${notif.fromUsername} comentó: ${notif.content}"
                    "message" -> "${notif.fromUsername} te envió un mensaje"
                    else -> notif.fromUsername
                },
                fontWeight = if (!notif.isRead) FontWeight.ExtraBold else FontWeight.Normal, 
                color = Color.White,
                fontSize = 14.sp
            ) 
        },
        supportingContent = { 
            Text(
                text = when(notif.type) {
                    "follow_request" -> "Toca para ver el perfil"
                    "message", "comment" -> notif.content
                    else -> "Nueva actividad"
                },
                color = Color.Gray,
                fontSize = 12.sp,
                maxLines = 1
            ) 
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (notif.type == "follow_request") {
                    Button(
                        onClick = onAccept, 
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurple), 
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Text("Aceptar", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
                if (!notif.isRead) {
                    Box(modifier = Modifier.size(10.dp).background(NeonPink, CircleShape))
                }
            }
        },
        leadingContent = { 
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(CardGray, CircleShape)
                    .border(1.dp, NeonPurple.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when(notif.type) {
                        "like" -> Icons.Default.Favorite
                        "comment" -> Icons.Default.Comment
                        "message" -> Icons.AutoMirrored.Filled.Chat
                        "follow_request" -> Icons.Default.PersonAdd
                        else -> Icons.Default.Notifications
                    },
                    contentDescription = null,
                    tint = if (notif.type == "like") NeonPink else NeonPurple,
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun MessagesListScreen(rootNavController: NavHostController) {
    val firestore = FirebaseFirestore.getInstance()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    var chats by remember { mutableStateOf<List<ChatRoom>>(emptyList()) }

    LaunchedEffect(currentUserId) {
        firestore.collection("chats")
            .whereArrayContains("participants", currentUserId)
            .addSnapshotListener { snapshot, _ ->
                chats = snapshot?.toObjects(ChatRoom::class.java) ?: emptyList()
            }
    }

    Column(modifier = Modifier.fillMaxSize().background(DeepSpace)) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Mensajes", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.weight(1f))
            Icon(Icons.Default.Edit, null, tint = NeonPurple)
        }
        LazyColumn {
            items(chats) { chat ->
                ChatItem(chat, currentUserId, rootNavController)
            }
        }
    }
}

@Composable
fun ChatItem(chat: ChatRoom, currentUserId: String, rootNavController: NavHostController) {
    val firestore = FirebaseFirestore.getInstance()
    val otherId = chat.participants.find { it != currentUserId } ?: ""
    var otherName by remember { mutableStateOf("Cargando...") }
    var otherPhoto by remember { mutableStateOf<String?>(null) }
    var unreadCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(otherId) {
        firestore.collection("users").document(otherId).get().addOnSuccessListener {
            otherName = it.getString("username") ?: "Usuario"
            otherPhoto = it.getString("photoUrl")
        }
    }

    LaunchedEffect(chat.id) {
        firestore.collection("chats").document(chat.id).collection("messages")
            .whereEqualTo("isRead", false)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    unreadCount = snapshot.documents.count { it.getString("senderId") != currentUserId }
                }
            }
    }

    ListItem(
        modifier = Modifier.clickable {
            val encodedName = Uri.encode(otherName)
            rootNavController.navigate("chat/${chat.id}/$encodedName/$otherId")
        },
        headlineContent = {
            Text(
                otherName,
                fontWeight = if (unreadCount > 0) FontWeight.ExtraBold else FontWeight.Bold,
                color = Color.White
            )
        },
        supportingContent = {
            Text(
                chat.lastMessage,
                fontWeight = if (unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
                color = if (unreadCount > 0) Color.White else Color.Gray,
                maxLines = 1
            )
        },
        trailingContent = {
            if (unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(NeonPink, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = unreadCount.toString(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        },
        leadingContent = {
            Box(
                modifier = Modifier.size(56.dp).border(2.dp, NeonPurple, CircleShape)
                    .padding(3.dp)
            ) {
                UserAvatar(otherPhoto, otherName, size = 50.dp)
            }
        },
        colors = ListItemDefaults.colors(containerColor = DeepSpace)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(navController: NavHostController, chatId: String, otherName: String, otherId: String) {
    var text by remember { mutableStateOf("") }
    val firestore = FirebaseFirestore.getInstance()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    val listState = rememberLazyListState()
    var otherUser by remember { mutableStateOf<User?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(otherId) {
        firestore.collection("users").document(otherId).get().addOnSuccessListener { 
            otherUser = it.toObject(User::class.java)
        }
    }

    LaunchedEffect(chatId) {
        firestore.collection("chats").document(chatId).collection("messages")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                messages = snapshot?.toObjects(Message::class.java) ?: emptyList()
                
                // Mark unread messages as read
                snapshot?.documents?.forEach { doc ->
                    if (doc.getString("senderId") != currentUserId && doc.getBoolean("isRead") == false) {
                        doc.reference.update("isRead", true)
                    }
                }
            }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    val mediaLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            scope.launch {
                val type = context.contentResolver.getType(it)
                val folder = if (type?.contains("video") == true) "videos" else "chat_media"
                val url = FirebaseRepository().uploadFile(it, folder)
                if (folder == "videos") {
                    FirebaseRepository().sendMessage(chatId, "", videoUrl = url)
                } else {
                    FirebaseRepository().sendMessage(chatId, "", imageUrl = url)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(otherUser?.photoUrl, otherName, 36.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(otherName, fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        bottomBar = {
            Surface(color = Color.Black, modifier = Modifier.navigationBarsPadding().imePadding()) {
                Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { mediaLauncher.launch("*/*") }) {
                        Icon(Icons.Default.AddCircleOutline, null, tint = NeonPurple)
                    }
                    TextField(
                        value = text, onValueChange = { text = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Enviar mensaje...") },
                        shape = RoundedCornerShape(24.dp),
                        colors = TextFieldDefaults.colors(focusedContainerColor = CardGray, unfocusedContainerColor = CardGray, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = {
                        if (text.isNotBlank()) {
                            scope.launch {
                                FirebaseRepository().sendMessage(chatId, text)
                                text = ""
                            }
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.Send, null, tint = NeonPurple, modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().background(DeepSpace).padding(padding)) {
            items(messages) { msg ->
                val isMe = msg.senderId == currentUserId
                ChatBubble(msg, isMe)
            }
        }
    }
}

@Composable
fun ChatBubble(msg: Message, isMe: Boolean) {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(
            color = if (isMe) NeonPurple else CardGray,
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp, 
                bottomStart = if (isMe) 16.dp else 4.dp, 
                bottomEnd = if (isMe) 4.dp else 16.dp
            )
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (!msg.imageUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = msg.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.sizeIn(maxWidth = 200.dp, maxHeight = 300.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
                if (!msg.videoUrl.isNullOrEmpty()) {
                    Box(
                        modifier = Modifier.size(200.dp).background(Color.Black, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PlayCircle, null, tint = Color.White, modifier = Modifier.size(48.dp))
                        Text("Vídeo", color = Color.White, modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp))
                    }
                }
                if (msg.text.isNotEmpty()) {
                    Text(msg.text, color = if (isMe) Color.Black else Color.White)
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(isGhostMode: Boolean, onGhostModeChange: (Boolean) -> Unit, rootNavController: NavHostController, repository: FirebaseRepository) {
    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()
    var user by remember { mutableStateOf<User?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val posts by repository.getUserPosts(auth.currentUser?.uid ?: "").collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        auth.currentUser?.uid?.let {
            firestore.collection("users").document(it).addSnapshotListener { snapshot, _ ->
                user = snapshot?.toObject(User::class.java)
            }
        }
    }

    val profileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { 
            scope.launch { 
                try {
                    repository.updateProfilePicture(it)
                    Toast.makeText(context, "Foto de perfil actualizada", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } 
        }
    }

    val postLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            scope.launch {
                try {
                    repository.createPost(it, "")
                    Toast.makeText(context, "Publicación subida", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
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
            
            Text(user?.username ?: "Cargando...", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(top = 12.dp))
            
            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ProfileStat(posts.size.toString(), "Posts") {}
                ProfileStat(user?.followersCount?.toString() ?: "0", "Seguidores") { 
                    rootNavController.navigate("userList/followers/${user?.uid}") 
                }
                ProfileStat(user?.followingCount?.toString() ?: "0", "Siguiendo") { 
                    rootNavController.navigate("userList/following/${user?.uid}") 
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { postLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
                ) {
                    Icon(Icons.Default.Add, null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Nueva Foto", color = Color.Black, fontWeight = FontWeight.Bold)
                }
                
                IconButton(
                    onClick = { 
                        auth.signOut()
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
fun ProfileStat(value: String, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Text(value, fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 20.sp)
        Text(label, color = Color.Gray, fontSize = 13.sp)
    }
}

@Composable
fun UserListScreen(navController: NavHostController, type: String, userId: String) {
    val firestore = FirebaseFirestore.getInstance()
    var users by remember { mutableStateOf<List<User>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(userId) {
        firestore.collection("users").document(userId).get().addOnSuccessListener { snapshot ->
            val uids = if (type == "followers") snapshot.get("followerUids") as? List<String> else snapshot.get("followingUids") as? List<String>
            if (!uids.isNullOrEmpty()) {
                firestore.collection("users").whereIn(FieldPath.documentId(), uids).get().addOnSuccessListener { 
                    users = it.toObjects(User::class.java)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(Color.Black).padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
                    Text(if (type == "followers") "Seguidores" else "Siguiendo", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                TextField(
                    value = searchQuery, onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    placeholder = { Text("Buscar...") },
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(focusedContainerColor = CardGray, unfocusedContainerColor = CardGray, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().background(DeepSpace).padding(padding)) {
            items(users.filter { it.username.contains(searchQuery, true) }) { user ->
                ListItem(
                    modifier = Modifier.clickable { navController.navigate("otherProfile/${user.uid}") },
                    headlineContent = { Text(user.username, fontWeight = FontWeight.Bold, color = Color.White) },
                    leadingContent = { UserAvatar(user.photoUrl, user.username, size = 44.dp) },
                    colors = ListItemDefaults.colors(containerColor = DeepSpace)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtherProfileScreen(navController: NavHostController, userId: String, repository: FirebaseRepository) {
    val firestore = FirebaseFirestore.getInstance()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    var user by remember { mutableStateOf<User?>(null) }
    var currentUser by remember { mutableStateOf<User?>(null) }
    val scope = rememberCoroutineScope()

    val posts by repository.getUserPosts(userId).collectAsState(initial = emptyList())

    LaunchedEffect(userId) {
        firestore.collection("users").document(userId).addSnapshotListener { snapshot, _ ->
            user = snapshot?.toObject(User::class.java)
        }
        firestore.collection("users").document(currentUserId).addSnapshotListener { snapshot, _ ->
            currentUser = snapshot?.toObject(User::class.java)
        }
    }

    val isFollowing = currentUser?.followingUids?.contains(userId) == true
    val isPending = user?.pendingFollowRequests?.contains(currentUserId) == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(user?.username ?: "", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().background(DeepSpace).padding(padding)) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                UserAvatar(user?.photoUrl, user?.username ?: "", size = 100.dp)
                Text(user?.username ?: "", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
                
                Row(modifier = Modifier.padding(vertical = 24.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ProfileStat(posts.size.toString(), "Posts") {}
                    ProfileStat(user?.followersCount?.toString() ?: "0", "Seguidores") {}
                    ProfileStat(user?.followingCount?.toString() ?: "0", "Siguiendo") {}
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            scope.launch {
                                if (isFollowing) {
                                    firestore.collection("users").document(currentUserId).update("followingUids", FieldValue.arrayRemove(userId), "followingCount", FieldValue.increment(-1))
                                    firestore.collection("users").document(userId).update("followerUids", FieldValue.arrayRemove(currentUserId), "followersCount", FieldValue.increment(-1))
                                } else if (!isPending) {
                                    firestore.collection("users").document(userId).update("pendingFollowRequests", FieldValue.arrayUnion(currentUserId))
                                    val notif = ActivityNotification(fromUserId = currentUserId, fromUsername = currentUser?.username ?: "", toUserId = userId, type = "follow_request")
                                    firestore.collection("notifications").add(notif)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (isFollowing || isPending) Color.DarkGray else NeonPurple)
                    ) {
                        Text(
                            text = when {
                                isFollowing -> "Siguiendo"
                                isPending -> "Pendiente"
                                else -> "Seguir"
                            },
                            color = if (isFollowing || isPending) Color.White else Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Button(onClick = {
                        val chatId = if (currentUserId < userId) "${currentUserId}_${userId}" else "${userId}_${currentUserId}"
                        firestore.collection("chats").document(chatId).set(mapOf("participants" to listOf(currentUserId, userId)), com.google.firebase.firestore.SetOptions.merge())
                        val encodedName = Uri.encode(user?.username ?: "Usuario")
                        navController.navigate("chat/$chatId/$encodedName/$userId")
                    }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)) { Text("Mensaje", color = Color.Black, fontWeight = FontWeight.Bold) }
                }
            }

            PostGrid(posts) { post ->
                navController.navigate("postDetail/${post.id}")
            }
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
            AsyncImage(
                model = post.imageUrl,
                contentDescription = null,
                modifier = Modifier.aspectRatio(1f).clickable { onPostClick(post) },
                contentScale = ContentScale.Crop
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(navController: NavHostController, postId: String, repository: FirebaseRepository) {
    val firestore = FirebaseFirestore.getInstance()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    var post by remember { mutableStateOf<Post?>(null) }
    val comments by repository.getComments(postId).collectAsState(initial = emptyList())
    var commentText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(postId) {
        firestore.collection("posts").document(postId).addSnapshotListener { snapshot, _ ->
            post = snapshot?.toObject(Post::class.java)
        }
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
                        colors = TextFieldDefaults.colors(focusedContainerColor = CardGray, unfocusedContainerColor = CardGray, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                        shape = RoundedCornerShape(24.dp)
                    )
                    IconButton(onClick = {
                        if (commentText.isNotBlank()) {
                            scope.launch {
                                repository.addComment(postId, commentText)
                                commentText = ""
                            }
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
                    Column {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            UserAvatar(null, p.username, 32.dp)
                            Text(p.username, modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        AsyncImage(
                            model = p.imageUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp),
                            contentScale = ContentScale.Fit
                        )
                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            val isLiked = p.likedBy.contains(currentUserId)
                            IconButton(onClick = { scope.launch { repository.toggleLike(postId) } }) {
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
                ListItem(
                    headlineContent = { Text(comment.username, fontWeight = FontWeight.Bold, color = Color.White) },
                    supportingContent = { Text(comment.text, color = Color.White) },
                    leadingContent = { UserAvatar(null, comment.username, 32.dp) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}

@Composable
fun VenueDetailSheet(venue: Venue, navController: NavHostController) {
    val firestore = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val currentUserId = auth.currentUser?.uid ?: return
    var attendees by remember { mutableStateOf<List<User>>(emptyList()) }
    var isAttending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(venue.id) {
        firestore.collection("attendances")
            .whereEqualTo("venueId", venue.id)
            .addSnapshotListener { snapshot, _ ->
                val attendeeIds = snapshot?.documents?.map { it.getString("userId") ?: "" } ?: emptyList()
                isAttending = attendeeIds.contains(currentUserId)
                
                if (attendeeIds.isNotEmpty()) {
                    firestore.collection("users")
                        .whereIn(FieldPath.documentId(), attendeeIds.take(10)) 
                        .get()
                        .addOnSuccessListener { userSnapshot ->
                            attendees = userSnapshot.toObjects(User::class.java)
                        }
                } else {
                    attendees = emptyList()
                }
            }
    }

    Column(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(venue.name, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = NeonPurple)
                Text("${venue.category} • ${venue.address}", color = Color.Gray, fontSize = 14.sp)
            }
            Surface(color = NeonPurple.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
                Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, null, tint = NeonPurple, modifier = Modifier.size(16.dp))
                    Text(venue.rating.toString(), color = NeonPurple, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
        
        Text("¿Quién va hoy?", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(top = 24.dp))
        LazyRow(modifier = Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            items(attendees) { attendee ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { navController.navigate("otherProfile/${attendee.uid}") }) {
                    UserAvatar(attendee.photoUrl, attendee.username, size = 50.dp)
                    Text(if (attendee.uid == currentUserId) "Tú" else attendee.username, fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                }
            }
            if (attendees.isEmpty()) {
                item { Text("Nadie apuntado aún 🔥", color = Color.DarkGray, fontSize = 14.sp) }
            }
        }

        Button(
            onClick = {
                scope.launch {
                    if (isAttending) {
                        val snapshot = firestore.collection("attendances").whereEqualTo("userId", currentUserId).whereEqualTo("venueId", venue.id).get().await()
                        snapshot.documents.forEach { it.reference.delete().await() }
                    } else {
                        firestore.collection("attendances").add(Attendance(userId = currentUserId, venueId = venue.id)).await()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (isAttending) Color.DarkGray else NeonPurple)
        ) {
            Icon(if (isAttending) Icons.Default.CheckCircle else Icons.Default.Add, null, tint = if (isAttending) NeonPurple else Color.Black)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isAttending) "ESTOY APUNTADO" else "¡ME APUNTO!", color = if (isAttending) Color.White else Color.Black, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavHostController) {
    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences("Settings", android.content.Context.MODE_PRIVATE)
    
    var isDarkMap by remember { mutableStateOf(sharedPrefs.getBoolean("dark_map", true)) }
    var isEnglish by remember { mutableStateOf(sharedPrefs.getBoolean("is_english", false)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().background(DeepSpace).padding(padding).padding(16.dp)) {
            Text("Preferencias de la App", color = NeonPurple, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(bottom = 16.dp))
            
            // Map Theme
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (isDarkMap) Icons.Default.DarkMode else Icons.Default.LightMode, null, tint = Color.White)
                Text("Mapa Oscuro", color = Color.White, modifier = Modifier.weight(1f).padding(start = 16.dp), fontSize = 16.sp)
                Switch(
                    checked = isDarkMap,
                    onCheckedChange = {
                        isDarkMap = it
                        sharedPrefs.edit().putBoolean("dark_map", it).apply()
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = NeonPurple, checkedTrackColor = NeonPurple.copy(alpha = 0.5f))
                )
            }
            
            HorizontalDivider(color = Color.DarkGray)
            
            // Language
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Language, null, tint = Color.White)
                Text("Idioma Inglés", color = Color.White, modifier = Modifier.weight(1f).padding(start = 16.dp), fontSize = 16.sp)
                Switch(
                    checked = isEnglish,
                    onCheckedChange = {
                        isEnglish = it
                        sharedPrefs.edit().putBoolean("is_english", it).apply()
                        
                        // En Compose se requiere usar strings.xml para traducir. Mostramos mensaje.
                        Toast.makeText(context, "Requiere extraer textos a strings.xml (Próximamente)", Toast.LENGTH_LONG).show()
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = NeonPurple, checkedTrackColor = NeonPurple.copy(alpha = 0.5f))
                )
            }

            HorizontalDivider(color = Color.DarkGray)
            
            // Ghost Mode stub
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VisibilityOff, null, tint = Color.White)
                Text("Modo Fantasma", color = Color.White, modifier = Modifier.weight(1f).padding(start = 16.dp), fontSize = 16.sp)
                Switch(
                    checked = false,
                    onCheckedChange = {
                        Toast.makeText(context, "Disponible en PM Premium", Toast.LENGTH_SHORT).show()
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = NeonPurple, checkedTrackColor = NeonPurple.copy(alpha = 0.5f))
                )
            }
        }
    }
}
