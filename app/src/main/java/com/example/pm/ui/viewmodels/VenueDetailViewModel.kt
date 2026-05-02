package com.example.pm.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pm.Attendance
import com.example.pm.ChatRoom
import com.example.pm.FirebaseRepository
import com.example.pm.Tag
import com.example.pm.User
import com.example.pm.Venue
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class VenueDetailViewModel @Inject constructor(
    private val repository: FirebaseRepository,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ViewModel() {

    val currentUserId: String = auth.currentUser?.uid ?: ""

    private val _attendees = MutableStateFlow<List<User>>(emptyList())
    val attendees: StateFlow<List<User>> = _attendees

    private val _isAttending = MutableStateFlow(false)
    val isAttending: StateFlow<Boolean> = _isAttending

    private val _availableTags = MutableStateFlow<List<Tag>>(emptyList())
    val availableTags: StateFlow<List<Tag>> = _availableTags

    private val _tagStats = MutableStateFlow<Map<String, Int>>(emptyMap())
    val tagStats: StateFlow<Map<String, Int>> = _tagStats

    private val _tagUsers = MutableStateFlow<Map<String, List<User>>>(emptyMap())
    val tagUsers: StateFlow<Map<String, List<User>>> = _tagUsers

    private val _attendancesCount = MutableStateFlow(0)
    val attendancesCount: StateFlow<Int> = _attendancesCount
    
    private val _otherVenueAttendance = MutableStateFlow<Venue?>(null)
    val otherVenueAttendance: StateFlow<Venue?> = _otherVenueAttendance

    private val _hasOtherAttendance = MutableStateFlow(false)
    val hasOtherAttendance: StateFlow<Boolean> = _hasOtherAttendance

    private val _currentVenueId = MutableStateFlow<String?>(null)

    // New states for invitation system
    private val _followers = MutableStateFlow<List<User>>(emptyList())
    val followers: StateFlow<List<User>> = _followers

    private val _chats = MutableStateFlow<List<ChatRoom>>(emptyList())
    val chats: StateFlow<List<ChatRoom>> = _chats

    private val _venueAttendeeUids = MutableStateFlow<Set<String>>(emptySet())
    val venueAttendeeUids: StateFlow<Set<String>> = _venueAttendeeUids

    val currentUserAttendance: StateFlow<Attendance?> = repository.getUserAttendanceFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        loadTags()
        observeUserAttendance()
        loadFollowersAndChats()
    }

    private fun loadTags() {
        viewModelScope.launch {
            repository.getAllAvailableTags().collect {
                _availableTags.value = it
            }
        }
    }

    private fun observeUserAttendance() {
        viewModelScope.launch {
            repository.getUserAttendanceFlow().collectLatest { attendance ->
                if (attendance != null) {
                    val venueId = _currentVenueId.value
                    _hasOtherAttendance.value = venueId != null && attendance.venueId != venueId
                    _otherVenueAttendance.value = repository.getVenueById(attendance.venueId)
                } else {
                    _hasOtherAttendance.value = false
                    _otherVenueAttendance.value = null
                }
            }
        }
    }

    private fun loadFollowersAndChats() {
        viewModelScope.launch {
            val user = repository.getCurrentUser()
            if (user != null) {
                _followers.value = repository.getFollowers(user.uid)
            }
        }
        viewModelScope.launch {
            repository.getChatsFlow().collect {
                _chats.value = it
            }
        }
    }

    fun loadVenueDetails(venueId: String) {
        _currentVenueId.value = venueId
        viewModelScope.launch {
            repository.getVenueAttendances(venueId).collect { attendances ->
                _attendancesCount.value = attendances.size
                _isAttending.value = attendances.any { it.userId == currentUserId }
                _venueAttendeeUids.value = attendances.map { it.userId }.toSet()
                
                val stats = mutableMapOf<String, Int>()
                attendances.forEach { attendance ->
                    attendance.selectedTags.forEach { tagName ->
                        stats[tagName] = (stats[tagName] ?: 0) + 1
                    }
                }
                _tagStats.value = stats

                val attendeeIds = attendances.map { it.userId }.filter { it.isNotEmpty() }.distinct()
                if (attendeeIds.isNotEmpty()) {
                    val userSnapshot = firestore.collection("users")
                        .whereIn(FieldPath.documentId(), attendeeIds.take(30)) 
                        .get()
                        .await()
                    val allFetchedUsers = userSnapshot.toObjects(User::class.java)
                    _attendees.value = allFetchedUsers

                    val usersByTag = mutableMapOf<String, MutableList<User>>()
                    attendances.forEach { attendance ->
                        val user = allFetchedUsers.find { it.uid == attendance.userId }
                        if (user != null) {
                            attendance.selectedTags.forEach { tagName ->
                                if (!usersByTag.containsKey(tagName)) {
                                    usersByTag[tagName] = mutableListOf()
                                }
                                usersByTag[tagName]?.add(user)
                            }
                        }
                    }
                    _tagUsers.value = usersByTag
                } else {
                    _attendees.value = emptyList()
                    _tagUsers.value = emptyMap()
                }
            }
        }
    }

    fun toggleAttendance(venueId: String, selectedTags: List<String> = emptyList()) {
        viewModelScope.launch {
            repository.toggleAttendance(venueId, selectedTags)
        }
    }

    fun clearCurrentAttendance() {
        viewModelScope.launch {
            repository.clearAttendance()
        }
    }

    fun sendInvitations(venueId: String, venueName: String, selectedUserIds: List<String>, selectedChatIds: List<String>) {
        viewModelScope.launch {
            repository.sendVenueInvitation(venueId, venueName, selectedUserIds, selectedChatIds)
        }
    }

    fun checkAttendanceConflict(targetVenueId: String, onResult: (Venue?) -> Unit) {
        viewModelScope.launch {
            val current = repository.getUserAttendance()
            if (current != null && current.venueId != targetVenueId) {
                val otherVenue = repository.getVenueById(current.venueId)
                onResult(otherVenue)
            } else {
                onResult(null)
            }
        }
    }
}
