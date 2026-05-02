package com.example.pm.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pm.ChatRoom
import com.example.pm.FirebaseRepository
import com.example.pm.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GroupDetailViewModel @Inject constructor(
    private val repository: FirebaseRepository
) : ViewModel() {

    private val _chatRoom = MutableStateFlow<ChatRoom?>(null)
    val chatRoom: StateFlow<ChatRoom?> = _chatRoom

    private val _participants = MutableStateFlow<List<User>>(emptyList())
    val participants: StateFlow<List<User>> = _participants

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser

    init {
        viewModelScope.launch {
            _currentUser.value = repository.getCurrentUser()
        }
    }

    fun loadGroup(chatId: String) {
        viewModelScope.launch {
            repository.getChatRoom(chatId).collect { room ->
                _chatRoom.value = room
                if (room != null) {
                    val userList = mutableListOf<User>()
                    room.participants.forEach { uid ->
                        repository.getOtherUser(uid)?.let { userList.add(it) }
                    }
                    _participants.value = userList
                }
            }
        }
    }

    fun leaveGroup(chatId: String, onLeft: () -> Unit) {
        viewModelScope.launch {
            repository.leaveGroup(chatId)
            onLeft()
        }
    }
}
