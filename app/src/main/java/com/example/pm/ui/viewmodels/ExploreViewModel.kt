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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val repository: FirebaseRepository,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts: StateFlow<List<Post>> = _posts

    private val _searchResults = MutableStateFlow<List<User>>(emptyList())
    val searchResults: StateFlow<List<User>> = _searchResults

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser

    init {
        loadGlobalPosts()
        loadCurrentUser()
    }

    private fun loadGlobalPosts() {
        viewModelScope.launch {
            repository.getGlobalPosts().collect {
                _posts.value = it
            }
        }
    }

    private fun loadCurrentUser() {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, _ ->
                _currentUser.value = snapshot?.toObject(User::class.java)
            }
    }

    fun searchUsers(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        val currentUid = auth.currentUser?.uid ?: return
        firestore.collection("users")
            .whereGreaterThanOrEqualTo("username", query.lowercase())
            .whereLessThanOrEqualTo("username", query.lowercase() + "\uf8ff")
            .get()
            .addOnSuccessListener { result ->
                _searchResults.value = result.toObjects(User::class.java).filter { it.uid != currentUid }
            }
    }

    fun followUser(targetUser: User) {
        val currentUserId = auth.currentUser?.uid ?: return
        val currentUser = _currentUser.value ?: return
        
        viewModelScope.launch {
            firestore.collection("users").document(targetUser.uid)
                .update("pendingFollowRequests", FieldValue.arrayUnion(currentUserId))

            val notif = ActivityNotification(
                fromUserId = currentUserId,
                fromUsername = currentUser.username,
                toUserId = targetUser.uid,
                type = "follow_request"
            )
            repository.sendNotification(notif)
        }
    }
}
