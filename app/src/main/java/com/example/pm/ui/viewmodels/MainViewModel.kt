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
        
        // Listener en tiempo real más flexible: buscamos todas las notificaciones
        // y filtramos localmente para garantizar que nada se escape
        firestore.collection("notifications")
            .whereEqualTo("toUserId", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("MainViewModel", "Error observing notifications", error)
                    return@addSnapshotListener
                }
                
                // Contamos solo las que NO tienen isRead: true
                val unreadCount = snapshot?.documents?.count { doc ->
                    val isRead = doc.getBoolean("isRead") ?: false
                    !isRead
                } ?: 0
                
                _unreadNotificationsCount.value = unreadCount
            }
    }

    fun logout() {
        auth.signOut()
    }
}
