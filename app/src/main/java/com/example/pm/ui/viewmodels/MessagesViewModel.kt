package com.example.pm.ui.viewmodels

import androidx.lifecycle.ViewModel
import com.example.pm.ChatRoom
import com.example.pm.FirebaseRepository
import com.example.pm.Message
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
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

    init {
        loadChats()
    }

    private fun loadChats() {
        if (currentUserId.isEmpty()) return
        firestore.collection("chats")
            .whereArrayContains("participants", currentUserId)
            .addSnapshotListener { snapshot, _ ->
                val chatList = snapshot?.toObjects(ChatRoom::class.java) ?: emptyList()
                // Ordenar por el timestamp más reciente del último mensaje
                _chats.value = chatList.sortedByDescending { it.lastTimestamp }
            }
    }

    fun getOtherUserInfo(otherId: String, onResult: (String, String?) -> Unit) {
        firestore.collection("users").document(otherId).get().addOnSuccessListener {
            val name = it.getString("username") ?: "Usuario"
            val photo = it.getString("photoUrl")
            onResult(name, photo)
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
}
