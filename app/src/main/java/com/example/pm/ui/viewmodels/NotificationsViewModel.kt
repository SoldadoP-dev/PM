package com.example.pm.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pm.ActivityNotification
import com.example.pm.FirebaseRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val repository: FirebaseRepository
) : ViewModel() {

    private val _notifications = MutableStateFlow<List<ActivityNotification>>(emptyList())
    
    val followRequests: StateFlow<List<ActivityNotification>> = _notifications.map { list ->
        list.filter { it.type == "follow_request" }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val generalNotifications: StateFlow<List<ActivityNotification>> = _notifications.map { list ->
        list.filter { it.type != "follow_request" }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        loadNotifications()
    }

    private fun loadNotifications() {
        val currentUserId = auth.currentUser?.uid ?: return
        firestore.collection("notifications")
            .whereEqualTo("toUserId", currentUserId)
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.toObjects(ActivityNotification::class.java) ?: emptyList()
                _notifications.value = list.sortedByDescending { it.timestamp }
            }
    }

    fun markAllAsRead() {
        val currentUserId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val unreadDocs = firestore.collection("notifications")
                    .whereEqualTo("toUserId", currentUserId)
                    .get().await()
                
                val batch = firestore.batch()
                var count = 0
                for (doc in unreadDocs.documents) {
                    val isRead = doc.getBoolean("isRead") ?: false
                    if (!isRead) {
                        batch.update(doc.reference, "isRead", true)
                        count++
                    }
                }
                if (count > 0) batch.commit().await()
            } catch (e: Exception) {
                android.util.Log.e("NotificationsViewModel", "Error marking all as read", e)
            }
        }
    }

    fun markAsRead(notificationId: String) {
        firestore.collection("notifications").document(notificationId).update("isRead", true)
    }

    fun acceptFollowRequest(notif: ActivityNotification) {
        val currentUserId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            firestore.collection("users").document(currentUserId).update(
                "followerUids", FieldValue.arrayUnion(notif.fromUserId),
                "followersCount", FieldValue.increment(1),
                "pendingFollowRequests", FieldValue.arrayRemove(notif.fromUserId)
            )
            firestore.collection("users").document(notif.fromUserId).update(
                "followingUids", FieldValue.arrayUnion(currentUserId),
                "followingCount", FieldValue.increment(1)
            )
            markAsRead(notif.id)
        }
    }

    fun respondToMeetup(notif: ActivityNotification, accept: Boolean) {
        viewModelScope.launch {
            val meetup = repository.getMeetupByChatId(notif.targetId)
            if (meetup != null) {
                repository.respondToMeetup(meetup.id, accept)
            }
            markAsRead(notif.id)
        }
    }

    fun deleteNotifications(notificationIds: List<String>) {
        viewModelScope.launch {
            try {
                val batch = firestore.batch()
                var count = 0
                for (id in notificationIds) {
                    batch.delete(firestore.collection("notifications").document(id))
                    count++
                    // batch max is 500
                    if (count == 500) {
                        batch.commit().await()
                        count = 0
                    }
                }
                if (count > 0) {
                    batch.commit().await()
                }
            } catch (e: Exception) {
                android.util.Log.e("NotificationsViewModel", "Error deleting notifications", e)
            }
        }
    }
}
