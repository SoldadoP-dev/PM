package com.example.pm.ui.viewmodels

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pm.ChatRoom
import com.example.pm.FirebaseRepository
import com.example.pm.Message
import com.example.pm.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: FirebaseRepository,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ViewModel() {

    val currentUserId: String = auth.currentUser?.uid ?: ""

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    private val _otherUser = MutableStateFlow<User?>(null)
    val otherUser: StateFlow<User?> = _otherUser

    private val _chatRoom = MutableStateFlow<ChatRoom?>(null)
    val chatRoom: StateFlow<ChatRoom?> = _chatRoom

    fun loadChat(chatId: String, otherId: String) {
        viewModelScope.launch {
            _otherUser.value = repository.getOtherUser(otherId)
            
            repository.getChatRoom(chatId).collect { room ->
                _chatRoom.value = room
            }
        }
        viewModelScope.launch {
            repository.getMessages(chatId).collect { msgs ->
                _messages.value = msgs
                
                // Mark messages as read
                msgs.forEach { msg ->
                    if (msg.senderId != currentUserId && !msg.isRead) {
                        firestore.collection("chats").document(chatId)
                            .collection("messages").document(msg.id)
                            .update("isRead", true)
                    }
                }
            }
        }
    }

    fun sendMessage(chatId: String, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            repository.sendMessage(chatId, text)
        }
    }

    fun sendMedia(chatId: String, uri: Uri, isVideo: Boolean) {
        viewModelScope.launch {
            val folder = if (isVideo) "videos" else "chat_media"
            val url = repository.uploadFile(uri, folder)
            if (isVideo) {
                repository.sendMessage(chatId, "", videoUrl = url)
            } else {
                repository.sendMessage(chatId, "", imageUrl = url)
            }
        }
    }

    fun setTyping(chatId: String, isTyping: Boolean) {
        repository.setTyping(chatId, isTyping)
    }
}
