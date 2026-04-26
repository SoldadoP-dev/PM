package com.example.pm.ui.viewmodels

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pm.ActivityNotification
import com.example.pm.FirebaseRepository
import com.example.pm.Post
import com.example.pm.User
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: FirebaseRepository,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user

    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts: StateFlow<List<Post>> = _posts

    private val _recommendedUsers = MutableStateFlow<List<User>>(emptyList())
    val recommendedUsers: StateFlow<List<User>> = _recommendedUsers

    private val _pendingRequests = MutableStateFlow<List<User>>(emptyList())
    val pendingRequests: StateFlow<List<User>> = _pendingRequests

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading

    val hasActiveStories: Flow<Boolean> = _user.flatMapLatest { user ->
        if (user == null) flowOf(false)
        else repository.getStoriesByUser(user.uid).map { it.isNotEmpty() }
    }

    init {
        loadProfile()
        loadRecommendedUsers()
        loadPendingRequests()
    }

    private fun loadProfile() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            repository.getCurrentUserFlow().collect {
                _user.value = it
                // Recargar recomendaciones si el usuario cambia (por ejemplo, al ocultar a alguien)
                loadRecommendedUsers()
            }
        }
        viewModelScope.launch {
            repository.getUserPosts(uid).collect {
                _posts.value = it
            }
        }
    }

    private fun loadRecommendedUsers() {
        viewModelScope.launch {
            val currentUser = repository.getCurrentUser() ?: return@launch
            val allUsers = repository.getAllUsers()
            
            val followingSet = currentUser.followingUids.toSet()
            val hiddenSet = currentUser.hiddenUids.toSet()
            
            val recommendations = allUsers.filter { otherUser ->
                otherUser.uid != currentUser.uid && 
                !followingSet.contains(otherUser.uid) &&
                !hiddenSet.contains(otherUser.uid)
            }.sortedByDescending { otherUser ->
                val otherFollowing = otherUser.followingUids.toSet()
                followingSet.intersect(otherFollowing).size
            }.take(20)
            
            _recommendedUsers.value = recommendations
        }
    }

    fun removeRecommendedUser(userId: String) {
        viewModelScope.launch {
            val myUid = auth.currentUser?.uid ?: return@launch
            firestore.collection("users").document(myUid)
                .update("hiddenUids", com.google.firebase.firestore.FieldValue.arrayUnion(userId))
                .await()
            // La actualización se reflejará a través del flow de loadProfile
        }
    }

    private fun loadPendingRequests() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            firestore.collection("notifications")
                .whereEqualTo("toUserId", uid)
                .whereEqualTo("type", "follow_request")
                .whereEqualTo("isRead", false)
                .addSnapshotListener { snapshot, _ ->
                    val userIds = snapshot?.documents?.mapNotNull { it.getString("fromUserId") } ?: emptyList()
                    viewModelScope.launch {
                        val users = userIds.mapNotNull { repository.getOtherUser(it) }
                        _pendingRequests.value = users.distinctBy { it.uid }
                    }
                }
        }
    }

    fun handleFollowRequest(fromUserId: String, accept: Boolean) {
        viewModelScope.launch {
            val myUid = auth.currentUser?.uid ?: return@launch
            if (accept) {
                firestore.collection("users").document(myUid)
                    .update("followerUids", com.google.firebase.firestore.FieldValue.arrayUnion(fromUserId),
                            "followersCount", com.google.firebase.firestore.FieldValue.increment(1)).await()
                
                firestore.collection("users").document(fromUserId)
                    .update("followingUids", com.google.firebase.firestore.FieldValue.arrayUnion(myUid),
                            "followingCount", com.google.firebase.firestore.FieldValue.increment(1)).await()
            }
            
            // Marcar notificación como leída o borrarla
            val snapshot = firestore.collection("notifications")
                .whereEqualTo("toUserId", myUid)
                .whereEqualTo("fromUserId", fromUserId)
                .whereEqualTo("type", "follow_request")
                .get().await()
            
            for (doc in snapshot.documents) {
                doc.reference.delete().await()
            }
        }
    }

    fun followUser(targetUserId: String) {
        viewModelScope.launch {
            val currentUser = repository.getCurrentUser() ?: return@launch
            // Aquí se enviaría una solicitud o se seguiría directamente según la privacidad del usuario
            // Por ahora, seguimiento directo
            firestore.collection("users").document(currentUser.uid)
                .update("followingUids", com.google.firebase.firestore.FieldValue.arrayUnion(targetUserId),
                        "followingCount", com.google.firebase.firestore.FieldValue.increment(1)).await()
            
            firestore.collection("users").document(targetUserId)
                .update("followerUids", com.google.firebase.firestore.FieldValue.arrayUnion(currentUser.uid),
                        "followersCount", com.google.firebase.firestore.FieldValue.increment(1)).await()
            
            repository.sendNotification(ActivityNotification(
                fromUserId = currentUser.uid,
                fromUsername = currentUser.username,
                toUserId = targetUserId,
                type = "follow",
                content = "${currentUser.username} ha empezado a seguirte"
            ))
        }
    }

    fun updateProfilePicture(uri: Uri) {
        viewModelScope.launch {
            repository.updateProfilePicture(uri)
        }
    }

    fun updateFullProfile(
        newUsername: String,
        newBio: String,
        newImageUri: Uri?,
        currentPassword: String,
        newPassword: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val user = auth.currentUser ?: return@launch
                if (newPassword.isNotEmpty()) {
                    if (currentPassword.isEmpty()) {
                        onError("Debes ingresar la contraseña actual para cambiarla")
                        return@launch
                    }
                    val credential = EmailAuthProvider.getCredential(user.email!!, currentPassword)
                    user.reauthenticate(credential).await()
                    user.updatePassword(newPassword).await()
                }
                val photoUrl = if (newImageUri != null) {
                    repository.uploadFile(newImageUri, "profile_pics")
                } else {
                    _user.value?.photoUrl ?: ""
                }
                repository.updateUser(newUsername, newBio, photoUrl)
                onSuccess()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Error desconocido")
            }
        }
    }

    fun createPost(uri: Uri, caption: String, isVideo: Boolean) {
        viewModelScope.launch {
            _isUploading.value = true
            try {
                repository.createPost(uri, caption, isVideo)
                delay(1000) 
                val uid = auth.currentUser?.uid ?: return@launch
                repository.getUserPosts(uid).first().let {
                    _posts.value = it
                }
            } catch (e: Exception) {
                android.util.Log.e("ProfileViewModel", "Error creating post", e)
            } finally {
                _isUploading.value = false
            }
        }
    }

    fun setGhostMode(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateGhostMode(enabled)
        }
    }

    fun logout() {
        auth.signOut()
    }
}
