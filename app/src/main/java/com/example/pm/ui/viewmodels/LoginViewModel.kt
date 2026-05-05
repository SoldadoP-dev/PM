package com.example.pm.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pm.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _loginEvent = MutableSharedFlow<LoginEvent>()
    val loginEvent = _loginEvent.asSharedFlow()

    private val _emailError = MutableStateFlow<Int?>(null)
    val emailError = _emailError.asStateFlow()

    private val _passwordError = MutableStateFlow<Int?>(null)
    val passwordError = _passwordError.asStateFlow()

    fun login(email: String, password: String) {
        _emailError.value = null
        _passwordError.value = null

        if (email.isBlank()) {
            _emailError.value = R.string.error_empty_fields
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _emailError.value = R.string.error_invalid_email
            return
        }
        if (password.isBlank()) {
            _passwordError.value = R.string.error_empty_fields
            return
        }

        viewModelScope.launch {
            try {
                auth.signInWithEmailAndPassword(email, password).await()
                _loginEvent.emit(LoginEvent.Success)
            } catch (e: FirebaseAuthInvalidUserException) {
                _emailError.value = R.string.error_user_not_found
            } catch (e: FirebaseAuthInvalidCredentialsException) {
                _passwordError.value = R.string.error_wrong_password
            } catch (e: Exception) {
                _loginEvent.emit(LoginEvent.Error(R.string.error_unknown))
            }
        }
    }

    sealed class LoginEvent {
        object Success : LoginEvent()
        data class Error(val messageRes: Int) : LoginEvent()
    }
}
