package com.example.assignment.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import android.util.Log

class LoginScreenHandle {
    fun login(email: String, password: String, callback: (Boolean, FirebaseUser?, String?) -> Unit) {
        Log.d("LoginScreenHandle", "Initiating login for email: $email")
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = FirebaseAuth.getInstance().currentUser
                    Log.d("LoginScreenHandle", "Login successful for user: ${user?.uid}")
                    callback(true, user, null)
                } else {
                    val message = task.exception?.message ?: "Unknown error"
                    Log.e("LoginScreenHandle", "Login failed: $message")
                    callback(false, null, message)
                }
            }
    }

    fun register(email: String, password: String, callback: (Boolean, FirebaseUser?, String?) -> Unit) {
        Log.d("LoginScreenHandle", "Initiating registration for email: $email")
        FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = FirebaseAuth.getInstance().currentUser
                    Log.d("LoginScreenHandle", "Registration successful for user: ${user?.uid}")
                    callback(true, user, null)
                } else {
                    val message = task.exception?.message ?: "Unknown error"
                    Log.e("LoginScreenHandle", "Registration failed: $message")
                    callback(false, null, message)
                }
            }
    }
}