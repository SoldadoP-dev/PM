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
import kotlinx.coroutines.flow.StateFlow
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
    val notifications: StateFlow<List<ActivityNotification>> = _notifications

    init {
        loadNotifications()
    }

    private fun loadNotifications() {
        val currentUserId = auth.currentUser?.uid ?: return
        firestore.collection("notifications")
            .whereEqualTo("toUserId", currentUserId)
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.toObjects(ActivityNotification::class.java) ?: emptyList()
                _notifications.value = list.sortedByDescending { it.timestamp }.take(50)
            }
    }

    fun markAllAsRead() {
        val currentUserId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            val unreadDocs = firestore.collection("notifications")
                .whereEqualTo("toUserId", currentUserId)
                .whereEqualTo("isRead", false)
                .get().await()
            for (doc in unreadDocs.documents) {
                doc.reference.update("isRead", true)
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
}
