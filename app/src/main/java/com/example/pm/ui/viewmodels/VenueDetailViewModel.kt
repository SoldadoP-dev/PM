package com.example.pm.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pm.FirebaseRepository
import com.example.pm.Tag
import com.example.pm.User
import com.example.pm.Venue
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
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

    private var currentVenueId: String? = null

    init {
        loadTags()
        observeUserAttendance()
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
                if (attendance != null && attendance.venueId != currentVenueId) {
                    _otherVenueAttendance.value = repository.getVenueById(attendance.venueId)
                } else {
                    _otherVenueAttendance.value = null
                }
            }
        }
    }

    fun loadVenueDetails(venueId: String) {
        currentVenueId = venueId
        viewModelScope.launch {
            // Verificar si ya tiene otra asistencia al cargar
            val userAttendance = repository.getUserAttendance()
            if (userAttendance != null && userAttendance.venueId != venueId) {
                _otherVenueAttendance.value = repository.getVenueById(userAttendance.venueId)
            } else {
                _otherVenueAttendance.value = null
            }

            repository.getVenueAttendances(venueId).collect { attendances ->
                _attendancesCount.value = attendances.size
                _isAttending.value = attendances.any { it.userId == currentUserId }
                
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
}
