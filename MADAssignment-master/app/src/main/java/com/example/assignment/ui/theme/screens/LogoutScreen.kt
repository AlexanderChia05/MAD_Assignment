package com.example.assignment.ui.theme.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.assignment.viewmodel.LoginScreenViewModel
import com.google.firebase.auth.FirebaseAuth
import android.util.Log

@Composable
fun LogoutScreen(navController: NavHostController, loginViewModel: LoginScreenViewModel) {
    Log.d("LogoutScreen", "LogoutScreen rendered")

    LaunchedEffect(Unit) {
        Log.d("LogoutScreen", "Performing Firebase sign-out")
        try {
            FirebaseAuth.getInstance().signOut()
        } catch (e: Exception) {
            Log.e("LogoutScreen", "Sign-out failed: ${e.message}", e)
        } finally {
            loginViewModel.currentUser.value = null
            loginViewModel.role.value = null

            // Replace the root stack with login in a single, safe place.
            // Do not make the target inclusive.
            navController.navigate("login") {
                popUpTo(navController.graph.startDestinationRoute ?: "login") {
                    inclusive = false
                }
                launchSingleTop = true
                restoreState = false
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().padding(60.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Logging Out...",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { contentDescription = "Logging out message" }
        )
    }
}
