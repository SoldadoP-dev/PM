package com.example.pm.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pm.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _registerEvent = MutableSharedFlow<RegisterEvent>()
    val registerEvent = _registerEvent.asSharedFlow()

    fun register(username: String, email: String, password: String) {
        viewModelScope.launch {
            try {
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                val user = User(uid = result.user!!.uid, username = username.lowercase())
                firestore.collection("users").document(user.uid).set(user).await()
                _registerEvent.emit(RegisterEvent.Success)
            } catch (e: Exception) {
                _registerEvent.emit(RegisterEvent.Error(e.message ?: "Error desconocido"))
            }
        }
    }

    sealed class RegisterEvent {
        object Success : RegisterEvent()
        data class Error(val message: String) : RegisterEvent()
    }
}
