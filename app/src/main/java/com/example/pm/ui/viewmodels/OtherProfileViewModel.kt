package com.example.pm.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pm.ActivityNotification
import com.example.pm.FirebaseRepository
import com.example.pm.Post
import com.example.pm.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OtherProfileViewModel @Inject constructor(
    private val repository: FirebaseRepository,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser

    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts: StateFlow<List<Post>> = _posts

    fun loadProfile(userId: String) {
        val currentUid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            // Escuchar cambios en tiempo real del usuario visitado a través del repositorio
            repository.getUserFlow(userId).collect {
                _user.value = it
            }
        }
        viewModelScope.launch {
            // Escuchar cambios en tiempo real del usuario actual a través del repositorio
            repository.getUserFlow(currentUid).collect {
                _currentUser.value = it
            }
        }
        viewModelScope.launch {
            // Cargar posts
            repository.getUserPosts(userId).collect {
                _posts.value = it
            }
        }
    }

    fun toggleFollow(userId: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        val user = _user.value ?: return
        val currentUser = _currentUser.value ?: return
        val isFollowing = currentUser.followingUids.contains(userId)
        val isPending = user.pendingFollowRequests.contains(currentUserId)

        viewModelScope.launch {
            if (isFollowing) {
                firestore.collection("users").document(currentUserId).update("followingUids", FieldValue.arrayRemove(userId), "followingCount", FieldValue.increment(-1))
                firestore.collection("users").document(userId).update("followerUids", FieldValue.arrayRemove(currentUserId), "followersCount", FieldValue.increment(-1))
            } else if (!isPending) {
                firestore.collection("users").document(userId).update("pendingFollowRequests", FieldValue.arrayUnion(currentUserId))
                val notif = ActivityNotification(fromUserId = currentUserId, fromUsername = currentUser.username, toUserId = userId, type = "follow_request")
                repository.sendNotification(notif)
            }
        }
    }

    fun getOrCreateChat(otherUserId: String, onResult: (String) -> Unit) {
        val currentUserId = auth.currentUser?.uid ?: return
        val chatId = if (currentUserId < otherUserId) "${currentUserId}_${otherUserId}" else "${otherUserId}_${currentUserId}"
        viewModelScope.launch {
            firestore.collection("chats").document(chatId).set(mapOf("participants" to listOf(currentUserId, otherUserId)), SetOptions.merge())
            onResult(chatId)
        }
    }
}
