package com.example.pm.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pm.ActivityNotification
import com.example.pm.Comment
import com.example.pm.FirebaseRepository
import com.example.pm.Post
import com.example.pm.User
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

    private val _feed = MutableStateFlow<List<Post>>(emptyList())
    val feed: StateFlow<List<Post>> = _feed

    private val _initialPage = MutableStateFlow(-1)
    val initialPage: StateFlow<Int> = _initialPage

    private val _post = MutableStateFlow<Post?>(null)
    val post: StateFlow<Post?> = _post

    private val _comments = MutableStateFlow<List<Comment>>(emptyList())
    val comments: StateFlow<List<Comment>> = _comments

    private val _firstLiker = MutableStateFlow<User?>(null)
    val firstLiker: StateFlow<User?> = _firstLiker

    val currentUser: Flow<User?> = repository.getCurrentUserFlow()

    val followingUsers: Flow<List<User>> = flow {
        val user = repository.getCurrentUser()
        if (user != null && user.followingUids.isNotEmpty()) {
            val users = mutableListOf<User>()
            user.followingUids.forEach { uid ->
                val other = repository.getOtherUser(uid)
                if (other != null) users.add(other)
            }
            emit(users)
        } else {
            emit(emptyList())
        }
    }

    fun loadFeed(contextStr: String, userId: String, initialPostId: String) {
        viewModelScope.launch {
            if (contextStr == "explore") {
                repository.getGlobalPosts().collect { list ->
                    _feed.value = list
                    if (_initialPage.value == -1) {
                        _initialPage.value = list.indexOfFirst { it.id == initialPostId }.coerceAtLeast(0)
                    }
                }
            } else if (contextStr == "profile") {
                repository.getUserPosts(userId).collect { list ->
                    _feed.value = list
                    if (_initialPage.value == -1) {
                        _initialPage.value = list.indexOfFirst { it.id == initialPostId }.coerceAtLeast(0)
                    }
                }
            } else {
                val p = repository.getPost(initialPostId)
                if (p != null) {
                    _feed.value = listOf(p)
                    _initialPage.value = 0
                }
            }
        }
    }

    fun onPageChanged(pagePost: Post) {
        _post.value = pagePost
        viewModelScope.launch {
            if (pagePost.likedBy.isNotEmpty()) {
                _firstLiker.value = repository.getOtherUser(pagePost.likedBy.first())
            } else {
                _firstLiker.value = null
            }
        }
        viewModelScope.launch {
            repository.getComments(pagePost.id).collect {
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
        
        _post.value = currentPost.copy(
            likedBy = currentLikedBy,
            likesCount = newLikesCount
        )

        viewModelScope.launch {
            repository.toggleLike(postId)
            if (currentLikedBy.isNotEmpty()) {
                _firstLiker.value = repository.getOtherUser(currentLikedBy.first())
            } else {
                _firstLiker.value = null
            }
        }
    }

    fun addComment(postId: String, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            repository.addComment(postId, text)
        }
    }

    fun toggleCommentLike(commentId: String) {
        val postId = _post.value?.id ?: return
        val comment = _comments.value.find { it.id == commentId } ?: return
        val currentLikedBy = comment.likedBy.toMutableList()
        val wasLiked = currentLikedBy.contains(currentUserId)
        var newLikesCount = comment.likesCount

        if (wasLiked) {
            currentLikedBy.remove(currentUserId)
            newLikesCount--
        } else {
            currentLikedBy.add(currentUserId)
            newLikesCount++
        }

        // Optimistic UI update for comments
        _comments.value = _comments.value.map {
            if (it.id == commentId) it.copy(likedBy = currentLikedBy, likesCount = newLikesCount) else it
        }

        viewModelScope.launch {
            repository.toggleCommentLike(postId, commentId)
        }
    }
}
