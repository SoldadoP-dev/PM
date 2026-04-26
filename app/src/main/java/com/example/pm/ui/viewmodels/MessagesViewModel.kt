package com.example.pm.ui.viewmodels

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pm.ChatRoom
import com.example.pm.FirebaseRepository
import com.example.pm.User
import com.example.pm.Story
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MessagesViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val repository: FirebaseRepository
) : ViewModel() {

    val currentUserId: String = auth.currentUser?.uid ?: ""

    private val _chats = MutableStateFlow<List<ChatRoom>>(emptyList())
    val chats: StateFlow<List<ChatRoom>> = _chats

    val currentUser: Flow<User?> = repository.getCurrentUserFlow()
    
    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading

    // Estado para saber si el usuario actual tiene historias
    val currentUserHasStories: Flow<Boolean> = currentUser.flatMapLatest { user ->
        if (user == null) flowOf(false)
        else repository.getStoriesByUser(user.uid).map { it.isNotEmpty() }
    }

    // Lista de usuarios seguidos que tienen historias activas
    val followingUsers: Flow<List<User>> = currentUser.flatMapLatest { user ->
        if (user == null || user.followingUids.isEmpty()) flowOf(emptyList())
        else flow {
            val users = mutableListOf<User>()
            user.followingUids.forEach { uid ->
                repository.getOtherUser(uid)?.let { users.add(it) }
            }
            emit(users)
        }
    }

    // Set de IDs de usuarios que tienen historias SIN VER por el usuario actual
    private val _unseenStoriesUserIds = MutableStateFlow<Set<String>>(emptySet())
    val unseenStoriesUserIds: StateFlow<Set<String>> = _unseenStoriesUserIds

    // Set de IDs de usuarios que tienen historias (vistas o no)
    private val _usersWithStories = MutableStateFlow<Set<String>>(emptySet())
    val usersWithStories: StateFlow<Set<String>> = _usersWithStories

    init {
        loadChats()
        observeFollowingStories()
    }

    private fun observeFollowingStories() {
        viewModelScope.launch {
            currentUser.collectLatest { user ->
                if (user != null) {
                    val targetUids = user.followingUids + user.uid
                    if (targetUids.isNotEmpty()) {
                        repository.getStories(targetUids).collect { stories ->
                            _usersWithStories.value = stories.map { it.userId }.toSet()
                            
                            // Filtrar usuarios que tienen al menos una historia que NO hemos visto
                            val unseenIds = stories.filter { story ->
                                !story.seenBy.contains(currentUserId)
                            }.map { it.userId }.toSet()
                            
                            _unseenStoriesUserIds.value = unseenIds
                        }
                    }
                }
            }
        }
    }

    private fun loadChats() {
        if (currentUserId.isEmpty()) return
        firestore.collection("chats")
            .whereArrayContains("participants", currentUserId)
            .addSnapshotListener { snapshot, _ ->
                val chatList = snapshot?.toObjects(ChatRoom::class.java) ?: emptyList()
                val activeChats = chatList.filter { chat ->
                    val deleteTime = chat.deletedTimestamps[currentUserId]
                    deleteTime == null || chat.lastTimestamp > deleteTime
                }
                _chats.value = activeChats.sortedByDescending { it.lastTimestamp }
            }
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            repository.deleteChatForUser(chatId)
        }
    }

    fun getOtherUserInfoFull(otherId: String, onResult: (User?) -> Unit) {
        firestore.collection("users").document(otherId).addSnapshotListener { snapshot, _ ->
            onResult(snapshot?.toObject(User::class.java))
        }
    }

    fun getUnreadCount(chatId: String): Flow<Int> = callbackFlow {
        val listener = firestore.collection("chats").document(chatId).collection("messages")
            .whereEqualTo("isRead", false)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val count = snapshot.documents.count { it.getString("senderId") != currentUserId }
                    trySend(count)
                }
            }
        awaitClose { listener.remove() }
    }

    fun uploadStory(uri: Uri, isVideo: Boolean) {
        viewModelScope.launch {
            _isUploading.value = true
            try {
                repository.uploadStory(uri, isVideo)
            } finally {
                _isUploading.value = false
            }
        }
    }
}
