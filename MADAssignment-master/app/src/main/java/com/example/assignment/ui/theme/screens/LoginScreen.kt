package com.example.assignment.ui.theme.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.assignment.viewmodel.LoginScreenViewModel
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.ui.graphics.Color
import android.util.Log
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.semantics

@Composable
fun LoginScreen(
    navController: NavController,
    loginViewModel: LoginScreenViewModel,
    role: String? = null
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedRole by rememberSaveable { mutableStateOf(role ?: "sales") }

    val currentUser by loginViewModel.currentUser
    val userRole by loginViewModel.role

    LaunchedEffect(currentUser, userRole) {
        if (currentUser != null && userRole != null) {
            when (userRole) {
                "sales" -> navController.navigate("sales") {
                    popUpTo("login") { inclusive = true }
                }
                "admin" -> navController.navigate("admin_area") {
                    popUpTo("login") { inclusive = true }
                }
                else -> {}
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp)
            .verticalScroll(rememberScrollState()),  // Enable vertical scrolling
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Role selection
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            SegmentedButton(
                selected = selectedRole == "sales",
                onClick = { selectedRole = "sales" },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) { Text("Sales Agent") }
            SegmentedButton(
                selected = selectedRole == "admin",
                onClick = { selectedRole = "admin" },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) { Text("Admin") }
        }

        Spacer(modifier = Modifier.height(30.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        Spacer(modifier = Modifier.height(30.dp))

        errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(50.dp))

        Button(
            onClick = {
                loginViewModel.login(email, password, selectedRole) { success, message ->
                    if (!success) errorMessage = message
                }
            },
            modifier = Modifier
                .width(100.dp)
                .height(36.dp)
        ) {
            Text("Login")
        }
        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = { navController.navigate("register") },
            modifier = Modifier
                .width(100.dp)
                .height(36.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.White,
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("Register")
        }
    }
}

