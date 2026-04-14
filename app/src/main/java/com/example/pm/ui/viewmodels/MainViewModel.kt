package com.example.pm.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pm.ActivityNotification
import com.example.pm.FirebaseRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val messaging: FirebaseMessaging,
    private val repository: FirebaseRepository
) : ViewModel() {

    private val _unreadNotificationsCount = MutableStateFlow(0)
    val unreadNotificationsCount: StateFlow<Int> = _unreadNotificationsCount

    init {
        updateFcmToken()
        observeNotifications()
    }

    private fun updateFcmToken() {
        val uid = auth.currentUser?.uid ?: return
        messaging.token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                firestore.collection("users").document(uid).update("fcmToken", task.result)
            }
        }
    }

    private fun observeNotifications() {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("notifications")
            .whereEqualTo("toUserId", uid)
            .whereEqualTo("isRead", false)
            .addSnapshotListener { snapshot, _ ->
                _unreadNotificationsCount.value = snapshot?.size() ?: 0
            }
    }

    fun logout() {
        auth.signOut()
    }
}
