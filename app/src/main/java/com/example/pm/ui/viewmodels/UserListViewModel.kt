package com.example.pm.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pm.User
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class UserListViewModel @Inject constructor(
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: StateFlow<List<User>> = _users

    fun loadUsers(type: String, userId: String) {
        viewModelScope.launch {
            try {
                val snapshot = firestore.collection("users").document(userId).get().await()
                val uids = if (type == "followers") {
                    snapshot.get("followerUids") as? List<String>
                } else {
                    snapshot.get("followingUids") as? List<String>
                }
                
                if (!uids.isNullOrEmpty()) {
                    val usersSnapshot = firestore.collection("users")
                        .whereIn(FieldPath.documentId(), uids)
                        .get()
                        .await()
                    _users.value = usersSnapshot.toObjects(User::class.java)
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}
