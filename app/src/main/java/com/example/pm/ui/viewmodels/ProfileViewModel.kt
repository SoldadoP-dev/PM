package com.example.pm.ui.viewmodels

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pm.FirebaseRepository
import com.example.pm.Post
import com.example.pm.User
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: FirebaseRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user

    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts: StateFlow<List<Post>> = _posts

    private val _isGhostMode = MutableStateFlow(false)
    val isGhostMode: StateFlow<Boolean> = _isGhostMode

    init {
        loadProfile()
    }

    private fun loadProfile() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _user.value = repository.getCurrentUser()
            repository.getUserPosts(uid).collect {
                _posts.value = it
            }
        }
    }

    fun updateProfilePicture(uri: Uri) {
        viewModelScope.launch {
            repository.updateProfilePicture(uri)
            _user.value = repository.getCurrentUser()
        }
    }

    fun createPost(uri: Uri, caption: String, isVideo: Boolean) {
        viewModelScope.launch {
            repository.createPost(uri, caption, isVideo)
        }
    }

    fun setGhostMode(enabled: Boolean) {
        _isGhostMode.value = enabled
    }

    fun logout() {
        auth.signOut()
    }
}
