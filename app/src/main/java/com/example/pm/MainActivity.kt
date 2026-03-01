package com.example.pm

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.pm.ui.theme.PMTheme
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// --- COLORES PREMIUM ---
val NeonPurple = Color(0xFFBB86FC)
val NeonPink = Color(0xFFFF4081)
val DeepSpace = Color(0xFF0A0A0A)
val CardGray = Color(0xFF1A1A1A)
val InstaGradient = Brush.linearGradient(listOf(Color(0xFF833AB4), Color(0xFFFD1D1D), Color(0xFFFCAF45)))

/**
 * Actividad principal de la aplicación. Configura el tema y la navegación raíz.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Habilita diseño de borde a borde (detrás de la barra de estado)
        setContent {
            PMTheme(darkTheme = true) { // Forzado a modo oscuro para estética nocturna
                AppNavigation()
            }
        }
    }
}

/**
 * Gestiona el flujo de navegación principal entre Login, Registro y la App Principal.
 */
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val auth = FirebaseAuth.getInstance()
    // Decide la pantalla de inicio según si hay una sesión activa
    val startDestination = if (auth.currentUser == null) "login" else "main"

    NavHost(
        navController = navController, 
        startDestination = startDestination,
        // Animaciones de transición tipo "Instagram" (deslizamiento lateral)
        enterTransition = { fadeIn(tween(400)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(400)) },
        exitTransition = { fadeOut(tween(400)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(400)) },
        popEnterTransition = { fadeIn(tween(400)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(400)) },
        popExitTransition = { fadeOut(tween(400)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(400)) }
    ) {
        composable("login") { LoginScreen(navController) }
        composable("register") { RegisterScreen(navController) }
        composable("main") { MainScreen(navController) }
        composable("notifications") { NotificationsScreen(navController) }
        
        // Pantalla de lista de seguidores/siguiendo
        composable("userList/{type}/{userId}") { backStackEntry ->
            val type = backStackEntry.arguments?.getString("type") ?: ""
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            UserListScreen(navController, type, userId)
        }
        
        // Pantalla de detalle de chat (Blindada contra caracteres especiales)
        composable("chat/{chatId}/{otherName}") { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId") ?: ""
            val otherName = Uri.decode(backStackEntry.arguments?.getString("otherName") ?: "Usuario")
            ChatDetailScreen(navController, chatId, otherName)
        }
        
        // Perfil de otros usuarios
        composable("otherProfile/{userId}") { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            OtherProfileScreen(navController, userId)
        }
    }
}

/**
 * Pantalla de inicio de sesión con Firebase Auth.
 */
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
        Text("PM", fontSize = 64.sp, fontWeight = FontWeight.ExtraBold, color = NeonPurple)
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

/**
 * Pantalla de registro de nuevos usuarios en Firebase y Firestore.
 */
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

/**
 * Contenedor principal que incluye el Scaffold, la TopBar y la BottomNavigationBar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(rootNavController: NavHostController) {
    val navController = rememberNavController()
    val sheetState = rememberModalBottomSheetState()
    var selectedVenue by remember { mutableStateOf<Venue?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var isGhostMode by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PM", fontWeight = FontWeight.ExtraBold, color = NeonPurple, fontSize = 24.sp) },
                actions = {
                    IconButton(onClick = { rootNavController.navigate("notifications") }) {
                        Icon(Icons.Default.FavoriteBorder, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        bottomBar = { BottomNavigationBar(navController) }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            NavigationGraph(
                navController = navController,
                isGhostMode = isGhostMode,
                onGhostModeChange = { isGhostMode = it },
                onVenueClick = { venue ->
                    selectedVenue = venue
                    showBottomSheet = true
                },
                rootNavController = rootNavController
            )
            
            if (showBottomSheet && selectedVenue != null) {
                ModalBottomSheet(
                    onDismissRequest = { showBottomSheet = false },
                    sheetState = sheetState,
                    containerColor = CardGray,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                ) {
                    VenueDetailSheet(selectedVenue!!) { showBottomSheet = false }
                }
            }
        }
    }
}

/**
 * Barra de navegación inferior con iconos dinámicos.
 */
@Composable
fun BottomNavigationBar(navController: NavHostController) {
    val items = listOf(
        Triple("home", Icons.Default.Map, "Mapa"),
        Triple("search", Icons.Default.Search, "Buscar"),
        Triple("messages", Icons.AutoMirrored.Filled.Chat, "Chats"),
        Triple("profile", Icons.Default.Person, "Perfil")
    )
    NavigationBar(containerColor = Color.Black) {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        items.forEach { (route, icon, label) ->
            NavigationBarItem(
                icon = { Icon(icon, null) },
                label = { Text(label, fontSize = 10.sp) },
                selected = currentRoute == route,
                onClick = {
                    navController.navigate(route) {
                        popUpTo("home") { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = NeonPurple,
                    unselectedIconColor = Color.Gray,
                    indicatorColor = NeonPurple.copy(alpha = 0.1f)
                )
            )
        }
    }
}

/**
 * Grafo de navegación interna para las secciones de la barra inferior.
 */
@Composable
fun NavigationGraph(
    navController: NavHostController, 
    isGhostMode: Boolean,
    onGhostModeChange: (Boolean) -> Unit,
    onVenueClick: (Venue) -> Unit,
    rootNavController: NavHostController
) {
    NavHost(navController, startDestination = "home") {
        composable("home") { HomeScreen(onVenueClick) }
        composable("search") { SearchScreen(rootNavController) }
        composable("messages") { MessagesListScreen(rootNavController) }
        composable("profile") { ProfileScreen(isGhostMode, onGhostModeChange, rootNavController) }
    }
}

/**
 * Pantalla principal que combina el carrusel de historias y el mapa.
 */
@Composable
fun HomeScreen(onVenueClick: (Venue) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(DeepSpace)) {
        StoriesRow()
        MapSection(onVenueClick)
    }
}

/**
 * Carrusel horizontal de historias superiores con borde degradado.
 */
@Composable
fun StoriesRow() {
    val stories = listOf("Tú", "Dani", "Elena", "Marc", "Sara", "Pau", "Lucas")
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(stories) { name ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .border(2.dp, InstaGradient, CircleShape)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(Color.DarkGray),
                    contentAlignment = Alignment.Center
                ) {
                    Text(name.take(1), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
                Text(name, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/**
 * Integración de Google Maps con buscador de locales real integrado.
 */
@SuppressLint("MissingPermission")
@Composable
fun MapSection(onVenueClick: (Venue) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(40.4168, -3.7038), 14f)
    }
    
    var hasPermission by remember { 
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) 
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                loc?.let { 
                    cameraPositionState.position = CameraPosition.fromLatLngZoom(LatLng(it.latitude, it.longitude), 15f) 
                }
            }
        } else launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val firestore = FirebaseFirestore.getInstance()
    var venues by remember { mutableStateOf<List<Venue>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        firestore.collection("venues").get().addOnSuccessListener { snapshot ->
            venues = snapshot.toObjects(Venue::class.java)
        }
    }

    // Filtrado de locales por nombre en tiempo real
    val filteredVenues = if (searchQuery.isBlank()) venues else venues.filter { it.name.contains(searchQuery, true) }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = hasPermission),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = true)
        ) {
            filteredVenues.forEach { venue ->
                Marker(
                    state = MarkerState(position = LatLng(venue.location.latitude, venue.location.longitude)),
                    title = venue.name,
                    onClick = { onVenueClick(venue); true }
                )
            }
        }

        // Barra de búsqueda flotante sobre el mapa
        Card(
            modifier = Modifier.align(Alignment.TopCenter).padding(16.dp).fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(27.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f)),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, null, tint = NeonPurple)
                Spacer(modifier = Modifier.width(12.dp))
                TextField(
                    value = searchQuery,
                    onValueChange = { query ->
                        searchQuery = query
                        // Vuelo de cámara al primer resultado encontrado
                        filteredVenues.firstOrNull()?.let { found ->
                            scope.launch {
                                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(found.location.latitude, found.location.longitude), 16f), 1000)
                            }
                        }
                    },
                    placeholder = { Text("Buscar discotecas...", color = Color.Gray) },
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                    singleLine = true
                )
            }
        }
    }
}

/**
 * Pantalla de búsqueda de usuarios con lógica de seguimiento real.
 */
@Composable
fun SearchScreen(rootNavController: NavHostController) {
    var searchQuery by remember { mutableStateOf("") }
    var searchResult by remember { mutableStateOf<List<User>>(emptyList()) }
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val firestore = FirebaseFirestore.getInstance()
    var currentUser by remember { mutableStateOf<User?>(null) }

    LaunchedEffect(currentUserId) {
        firestore.collection("users").document(currentUserId)
            .addSnapshotListener { snapshot, _ ->
                currentUser = snapshot?.toObject(User::class.java)
            }
    }

    Column(modifier = Modifier.fillMaxSize().background(DeepSpace).padding(16.dp)) {
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
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Buscar amigos...") },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonPurple, unfocusedBorderColor = Color.DarkGray)
        )

        LazyColumn(modifier = Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(searchResult) { user ->
                UserSearchItem(user, currentUser, rootNavController)
            }
        }
    }
}

/**
 * Elemento individual de la lista de búsqueda de usuarios.
 */
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
            Box(modifier = Modifier.size(45.dp).background(Color.DarkGray, CircleShape), contentAlignment = Alignment.Center) {
                Text(user.username.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
            }
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

/**
 * Pantalla que muestra las notificaciones de actividad del usuario.
 */
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
                notifications = snapshot?.toObjects(ActivityNotification::class.java) ?: emptyList()
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().background(DeepSpace).padding(padding)) {
            items(notifications) { notif ->
                NotificationItem(notif) {
                    scope.launch {
                        if (notif.type == "follow_request") {
                            firestore.collection("users").document(currentUserId).update("followerUids", FieldValue.arrayUnion(notif.fromUserId), "followersCount", FieldValue.increment(1), "pendingFollowRequests", FieldValue.arrayRemove(notif.fromUserId))
                            firestore.collection("users").document(notif.fromUserId).update("followingUids", FieldValue.arrayUnion(currentUserId), "followingCount", FieldValue.increment(1))
                            firestore.collection("notifications").document(notif.id).delete()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Elemento individual de notificación.
 */
@Composable
fun NotificationItem(notif: ActivityNotification, onAccept: () -> Unit) {
    ListItem(
        headlineContent = { Text(notif.fromUsername, fontWeight = FontWeight.Bold, color = Color.White) },
        supportingContent = { Text("quiere seguirte", color = Color.Gray) },
        trailingContent = {
            Button(onClick = onAccept, colors = ButtonDefaults.buttonColors(containerColor = NeonPurple), shape = RoundedCornerShape(8.dp)) {
                Text("Aceptar", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        leadingContent = { Box(modifier = Modifier.size(48.dp).background(Color.DarkGray, CircleShape)) },
        colors = ListItemDefaults.colors(containerColor = DeepSpace)
    )
}

/**
 * Pantalla con la lista de chats activos del usuario.
 */
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
                val otherId = chat.participants.find { it != currentUserId } ?: ""
                var otherName by remember { mutableStateOf("Cargando...") }
                
                LaunchedEffect(otherId) {
                    firestore.collection("users").document(otherId).get().addOnSuccessListener { 
                        otherName = it.getString("username") ?: "Usuario"
                    }
                }

                ListItem(
                    modifier = Modifier.clickable { 
                        // SEGURIDAD: Codificamos el nombre para evitar crash en navegación
                        val encodedName = Uri.encode(otherName)
                        rootNavController.navigate("chat/${chat.id}/$encodedName") 
                    },
                    headlineContent = { Text(otherName, fontWeight = FontWeight.Bold, color = Color.White) },
                    supportingContent = { Text(chat.lastMessage, color = Color.Gray, maxLines = 1) },
                    leadingContent = { 
                        Box(modifier = Modifier.size(56.dp).border(2.dp, NeonPurple, CircleShape).padding(3.dp).clip(CircleShape).background(Color.Gray)) 
                    },
                    colors = ListItemDefaults.colors(containerColor = DeepSpace)
                )
            }
        }
    }
}

/**
 * Pantalla de conversación individual con auto-scroll y burbujas de chat.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(navController: NavHostController, chatId: String, otherName: String) {
    var text by remember { mutableStateOf("") }
    val firestore = FirebaseFirestore.getInstance()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    val listState = rememberLazyListState()

    LaunchedEffect(chatId) {
        firestore.collection("chats").document(chatId).collection("messages")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                messages = snapshot?.toObjects(Message::class.java) ?: emptyList()
            }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(32.dp).background(Color.Gray, CircleShape))
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
            Surface(color = Color.Black, modifier = Modifier.imePadding()) {
                Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
                            val msg = Message(senderId = currentUserId, text = text)
                            firestore.collection("chats").document(chatId).collection("messages").add(msg)
                            firestore.collection("chats").document(chatId).update("lastMessage", text, "lastTimestamp", FieldValue.serverTimestamp())
                            text = ""
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
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart) {
                    Surface(
                        color = if (isMe) NeonPurple else CardGray,
                        shape = RoundedCornerShape(
                            topStart = 16.dp, topEnd = 16.dp, 
                            bottomStart = if (isMe) 16.dp else 4.dp, 
                            bottomEnd = if (isMe) 4.dp else 16.dp
                        )
                    ) {
                        Text(msg.text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), color = if (isMe) Color.Black else Color.White)
                    }
                }
            }
        }
    }
}

/**
 * Pantalla de perfil personal del usuario.
 */
@Composable
fun ProfileScreen(isGhostMode: Boolean, onGhostChange: (Boolean) -> Unit, rootNavController: NavHostController) {
    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()
    var user by remember { mutableStateOf<User?>(null) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        auth.currentUser?.uid?.let {
            firestore.collection("users").document(it).addSnapshotListener { snapshot, _ ->
                user = snapshot?.toObject(User::class.java)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(DeepSpace).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(100.dp).border(3.dp, InstaGradient, CircleShape).padding(5.dp).background(Color.DarkGray, CircleShape), contentAlignment = Alignment.Center) {
            Text(user?.username?.take(1)?.uppercase() ?: "?", fontSize = 40.sp, color = Color.White, fontWeight = FontWeight.Bold)
        }
        Text(user?.username ?: "Cargando...", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(top = 12.dp))
        
        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ProfileStat(user?.followersCount?.toString() ?: "0", "Seguidores") { 
                rootNavController.navigate("userList/followers/${user?.uid}") 
            }
            ProfileStat(user?.followingCount?.toString() ?: "0", "Siguiendo") { 
                rootNavController.navigate("userList/following/${user?.uid}") 
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Switch para activar el Modo Fantasma (Privacidad)
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CardGray), shape = RoundedCornerShape(20.dp)) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (isGhostMode) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = if (isGhostMode) NeonPink else NeonPurple)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Modo Fantasma", fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Posición oculta en el mapa", fontSize = 12.sp, color = Color.Gray)
                }
                Switch(checked = isGhostMode, onCheckedChange = onGhostChange, colors = SwitchDefaults.colors(checkedThumbColor = NeonPurple))
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))

        // Botón de Cerrar Sesión con estética Outlined
        OutlinedButton(
            onClick = { 
                auth.signOut()
                Toast.makeText(context, "Sesión cerrada", Toast.LENGTH_SHORT).show()
                rootNavController.navigate("login") { popUpTo(0) } 
            },
            modifier = Modifier.fillMaxWidth().height(54.dp).padding(bottom = 8.dp),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, NeonPurple.copy(alpha = 0.5f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonPurple)
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text("Cerrar Sesión", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun ProfileStat(value: String, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Text(value, fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 20.sp)
        Text(label, color = Color.Gray, fontSize = 13.sp)
    }
}

/**
 * Pantalla genérica para mostrar listas de Seguidores o Siguiendo.
 */
@Composable
fun UserListScreen(navController: NavHostController, type: String, userId: String) {
    val firestore = FirebaseFirestore.getInstance()
    var users by remember { mutableStateOf<List<User>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(userId) {
        firestore.collection("users").document(userId).get().addOnSuccessListener { snapshot ->
            val uids = if (type == "followers") snapshot.get("followerUids") as? List<String> else snapshot.get("followingUids") as? List<String>
            if (!uids.isNullOrEmpty()) {
                firestore.collection("users").whereIn("uid", uids).get().addOnSuccessListener { 
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
                    leadingContent = { Box(modifier = Modifier.size(44.dp).background(Color.Gray, CircleShape)) },
                    colors = ListItemDefaults.colors(containerColor = DeepSpace)
                )
            }
        }
    }
}

/**
 * Pantalla para visualizar el perfil de otros usuarios.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtherProfileScreen(navController: NavHostController, userId: String) {
    val firestore = FirebaseFirestore.getInstance()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    var user by remember { mutableStateOf<User?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(userId) {
        firestore.collection("users").document(userId).addSnapshotListener { snapshot, _ ->
            user = snapshot?.toObject(User::class.java)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(user?.username ?: "", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().background(DeepSpace).padding(padding).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(100.dp).border(2.dp, InstaGradient, CircleShape).padding(4.dp).background(Color.DarkGray, CircleShape))
            Text(user?.username ?: "", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
            
            Row(modifier = Modifier.padding(vertical = 24.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ProfileStat(user?.followersCount?.toString() ?: "0", "Seguidores") {}
                ProfileStat(user?.followingCount?.toString() ?: "0", "Siguiendo") {}
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = {
                    scope.launch {
                        firestore.collection("users").document(currentUserId).update("followingUids", FieldValue.arrayRemove(userId), "followingCount", FieldValue.increment(-1))
                        firestore.collection("users").document(userId).update("followerUids", FieldValue.arrayRemove(currentUserId), "followersCount", FieldValue.increment(-1))
                    }
                }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = CardGray)) { Text("Siguiendo", color = Color.White) }
                
                Button(onClick = {
                    val chatId = if (currentUserId < userId) "${currentUserId}_${userId}" else "${userId}_${currentUserId}"
                    firestore.collection("chats").document(chatId).set(mapOf("participants" to listOf(currentUserId, userId)), com.google.firebase.firestore.SetOptions.merge())
                    val encodedName = Uri.encode(user?.username ?: "Usuario")
                    navController.navigate("chat/$chatId/$encodedName")
                }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)) { Text("Mensaje", color = Color.Black, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/**
 * Panel inferior que muestra los detalles de una discoteca al pulsar su marcador.
 */
@Composable
fun VenueDetailSheet(venue: Venue, onAttend: () -> Unit) {
    Column(modifier = Modifier.padding(32.dp).fillMaxWidth()) {
        Text(venue.name, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = NeonPurple)
        Text(venue.category, color = Color.Gray, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onAttend, 
            modifier = Modifier.fillMaxWidth().height(60.dp), 
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
        ) { 
            Text("¡CONFIRMAR ASISTENCIA!", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) 
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
