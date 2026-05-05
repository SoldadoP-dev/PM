package com.example.pm.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pm.R
import com.example.pm.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _usernameError = MutableStateFlow<Int?>(null)
    val usernameError = _usernameError.asStateFlow()

    private val _emailError = MutableStateFlow<Int?>(null)
    val emailError = _emailError.asStateFlow()

    private val _passwordError = MutableStateFlow<Int?>(null)
    val passwordError = _passwordError.asStateFlow()

    fun register(username: String, email: String, password: String) {
        _usernameError.value = null
        _emailError.value = null
        _passwordError.value = null

        var hasError = false

        if (username.isBlank()) {
            _usernameError.value = R.string.error_empty_fields
            hasError = true
        }

        if (email.isBlank()) {
            _emailError.value = R.string.error_empty_fields
            hasError = true
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _emailError.value = R.string.error_invalid_email
            hasError = true
        }

        if (password.isBlank()) {
            _passwordError.value = R.string.error_empty_fields
            hasError = true
        } else if (password.length < 6) {
            _passwordError.value = R.string.error_password_too_short
            hasError = true
        } else if (password.none { it.isUpperCase() }) {
            _passwordError.value = R.string.error_password_no_upper
            hasError = true
        }

        if (hasError) return

        viewModelScope.launch {
            try {
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                val user = User(uid = result.user!!.uid, username = username.lowercase())
                firestore.collection("users").document(user.uid).set(user).await()
                _registerEvent.emit(RegisterEvent.Success)
            } catch (e: FirebaseAuthUserCollisionException) {
                _emailError.value = R.string.error_email_already_in_use
            } catch (e: Exception) {
                _registerEvent.emit(RegisterEvent.Error(R.string.error_unknown))
            }
        }
    }

    sealed class RegisterEvent {
        object Success : RegisterEvent()
        data class Error(val messageRes: Int) : RegisterEvent()
    }
}
