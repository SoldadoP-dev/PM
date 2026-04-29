package com.example.pm

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.pm.ui.screens.*
import com.example.pm.ui.theme.PMTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var auth: FirebaseAuth

    private var notificationListener: ListenerRegistration? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, getString(R.string.notifications_off), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        checkAndRequestNotificationPermission()

        auth.addAuthStateListener { firebaseAuth ->
            val uid = firebaseAuth.currentUser?.uid
            if (uid != null) {
                startNotificationMonitor(uid)
                setUserOnlineStatus(uid, true)
            } else {
                notificationListener?.remove()
                notificationListener = null
            }
        }

        // Observador global para el estado online/offline
        ProcessLifecycleOwner.get().lifecycle.addObserver(LifecycleEventObserver { _, event ->
            val uid = auth.currentUser?.uid ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_START -> setUserOnlineStatus(uid, true)
                Lifecycle.Event.ON_STOP -> setUserOnlineStatus(uid, false)
                else -> {}
            }
        })

        setContent {
            PMTheme(darkTheme = true) {
                val navController = rememberNavController()
                val context = LocalContext.current
                
                LaunchedEffect(Unit) {
                    val intent = (context as? MainActivity)?.intent
                    intent?.let { handleNotificationIntent(it, navController) }
                }

                AppNavigation(navController, auth)
            }
        }
    }

    private fun setUserOnlineStatus(uid: String, isOnline: Boolean) {
        FirebaseFirestore.getInstance().collection("users").document(uid)
            .update("isOnline", isOnline)
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
                "like", "comment", "comment_like" -> {
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

        notificationListener = FirebaseFirestore.getInstance().collection("notifications")
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
                                "comment_like" -> "Le gusta tu respuesta"
                                "follow_request" -> "Solicitud de seguimiento"
                                else -> "Notificación de PM"
                            }
                            val message = when(notif.type) {
                                "message", "comment" -> notif.content
                                "like" -> "${notif.fromUsername} le dio like a tu post"
                                "comment_like" -> "A ${notif.fromUsername} le gusta tu comentario"
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
}

@Composable
fun AppNavigation(navController: NavHostController, auth: FirebaseAuth) {
    val startDestination = if (auth.currentUser == null) "login" else "main"

    NavHost(
        navController = navController, 
        startDestination = startDestination,
        enterTransition = { fadeIn(tween(400)) },
        exitTransition = { fadeOut(tween(400)) }
    ) {
        composable("login") { LoginScreen(navController) }
        composable("register") { RegisterScreen(navController) }
        
        // Ruta única para main con parámetro opcional de pestaña
        composable(
            route = "main?tab={tab}",
            arguments = listOf(navArgument("tab") { 
                type = NavType.StringType
                defaultValue = "0" 
            })
        ) { backStackEntry ->
            val tab = backStackEntry.arguments?.getString("tab")?.toIntOrNull() ?: 0
            MainScreen(navController, initialTab = tab)
        }

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
            OtherProfileScreen(navController, userId)
        }
        
        composable("storyView/{userId}") { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            StoryViewScreen(navController, userId)
        }

        composable("postDetail/{postId}") { backStackEntry ->
            val postId = backStackEntry.arguments?.getString("postId") ?: ""
            PostDetailScreen(navController, postId)
        }

        composable("discoverPeople") { DiscoverPeopleScreen(navController) }
        
        composable("settings") { SettingsScreen(navController) }
        composable("editProfile") { EditProfileScreen(navController) }
    }
}
