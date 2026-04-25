package com.example.pm.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pm.Comment
import com.example.pm.FirebaseRepository
import com.example.pm.Post
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PostDetailViewModel @Inject constructor(
    private val repository: FirebaseRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    val currentUserId: String = auth.currentUser?.uid ?: ""

    private val _post = MutableStateFlow<Post?>(null)
    val post: StateFlow<Post?> = _post

    private val _comments = MutableStateFlow<List<Comment>>(emptyList())
    val comments: StateFlow<List<Comment>> = _comments

    fun loadPost(postId: String) {
        viewModelScope.launch {
            val p = repository.getPost(postId)
            _post.value = p
        }
        viewModelScope.launch {
            repository.getComments(postId).collect {
                _comments.value = it
            }
        }
    }

    suspend fun getUserPhoto(userId: String): String? {
        if (userId.isBlank()) return null
        return repository.getOtherUser(userId)?.photoUrl
    }

    fun deletePost(postId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.deletePost(postId)
            onSuccess()
        }
    }

    fun toggleLike(postId: String) {
        val currentPost = _post.value ?: return
        val currentLikedBy = currentPost.likedBy.toMutableList()
        val wasLiked = currentLikedBy.contains(currentUserId)
        var newLikesCount = currentPost.likesCount
        
        if (wasLiked) {
            currentLikedBy.remove(currentUserId)
            newLikesCount--
        } else {
            currentLikedBy.add(currentUserId)
            newLikesCount++
        }
        
        // Optimistic UI update
        _post.value = currentPost.copy(
            likedBy = currentLikedBy,
            likesCount = newLikesCount
        )

        viewModelScope.launch {
            repository.toggleLike(postId)
        }
    }

    fun addComment(postId: String, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            repository.addComment(postId, text)
        }
    }
}
