package com.example.assignment.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.example.assignment.data.LoginScreenHandle
import com.google.firebase.auth.FirebaseUser
import android.util.Log
import com.google.firebase.auth.FirebaseAuth

class LoginScreenViewModel(private val loginHandle: LoginScreenHandle) : ViewModel() {
    val currentUser = mutableStateOf(FirebaseAuth.getInstance().currentUser)
    val role = mutableStateOf<String?>(null)

    fun login(email: String, password: String, role: String?, callback: (Boolean, String?) -> Unit) {
        Log.d("LoginScreenViewModel", "Attempting login for email: $email, role: $role")
        if (email.isEmpty() || password.isEmpty()) {
            callback(false, "Email and password are required")
            return
        }

        loginHandle.login(email, password) { success, user, message ->
            if (success && user != null) {
                currentUser.value = user
                this.role.value = role
                Log.d("LoginScreenViewModel", "Login successful for user: ${user.uid}")
                callback(true, null)
            } else {
                Log.e("LoginScreenViewModel", "Login failed: $message")
                callback(false, message ?: "Login failed")
            }
        }
    }

    fun register(email: String, password: String, callback: (Boolean, String?) -> Unit) {
        Log.d("LoginScreenViewModel", "Attempting registration for email: $email")
        if (email.isEmpty() || password.isEmpty()) {
            callback(false, "Email and password are required")
            return
        }

        loginHandle.register(email, password) { success, user, message ->
            if (success && user != null) {
                currentUser.value = user
                Log.d("LoginScreenViewModel", "Registration successful for user: ${user.uid}")
                callback(true, null)
            } else {
                Log.e("LoginScreenViewModel", "Registration failed: $message")
                callback(false, message ?: "Registration failed")
            }
        }
    }
}
