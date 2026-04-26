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

    private val _isUploadingMedia = MutableStateFlow(false)
    val isUploadingMedia: StateFlow<Boolean> = _isUploadingMedia

    fun loadChat(chatId: String, otherId: String) {
        viewModelScope.launch {
            _otherUser.value = repository.getOtherUser(otherId)
            
            repository.getChatRoom(chatId).collect { room ->
                _chatRoom.value = room
            }
        }

        viewModelScope.launch {
            // Combinamos los mensajes con la info del chat para saber la fecha de borrado
            combine(
                repository.getMessages(chatId),
                repository.getChatRoom(chatId)
            ) { msgs, room ->
                val deleteTime = room?.deletedTimestamps?.get(currentUserId)
                if (deleteTime != null) {
                    // Solo mostramos mensajes posteriores a cuando borraste el chat
                    msgs.filter { it.timestamp > deleteTime }
                } else {
                    msgs
                }
            }.collect { filteredMsgs ->
                _messages.value = filteredMsgs
                
                // Marcar como leídos solo los mensajes visibles
                filteredMsgs.forEach { msg ->
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
            _isUploadingMedia.value = true
            try {
                if (isVideo) {
                    repository.createChatMessageVideo(chatId, uri)
                } else {
                    val url = repository.uploadFile(uri, "chat_media")
                    repository.sendMessage(chatId, "", imageUrl = url)
                }
            } finally {
                _isUploadingMedia.value = false
            }
        }
    }

    fun setTyping(chatId: String, isTyping: Boolean) {
        repository.setTyping(chatId, isTyping)
    }
}
