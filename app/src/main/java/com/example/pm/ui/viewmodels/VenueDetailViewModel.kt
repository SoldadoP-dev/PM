package com.example.pm.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pm.Attendance
import com.example.pm.FirebaseRepository
import com.example.pm.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    fun loadVenueDetails(venueId: String) {
        viewModelScope.launch {
            firestore.collection("attendances")
                .whereEqualTo("venueId", venueId)
                .addSnapshotListener { snapshot, _ ->
                    val attendeeIds = snapshot?.documents?.map { it.getString("userId") ?: "" } ?: emptyList()
                    _isAttending.value = attendeeIds.contains(currentUserId)
                    
                    if (attendeeIds.isNotEmpty()) {
                        viewModelScope.launch {
                            val userSnapshot = firestore.collection("users")
                                .whereIn(FieldPath.documentId(), attendeeIds.take(10)) 
                                .get()
                                .await()
                            _attendees.value = userSnapshot.toObjects(User::class.java)
                        }
                    } else {
                        _attendees.value = emptyList()
                    }
                }
        }
    }

    fun toggleAttendance(venueId: String) {
        viewModelScope.launch {
            repository.toggleAttendance(venueId)
        }
    }
}
