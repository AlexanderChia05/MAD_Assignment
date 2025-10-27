package com.example.assignment.ui.theme.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentProfileScreen(navController: NavController) {

    // Ensure back from OS also returns to dashboard inside the same graph
    BackHandler {
        navController.navigate("sales_dashboard") {
            launchSingleTop = true
            restoreState = true
            popUpTo("sales_dashboard") { inclusive = false }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Profile") },
                navigationIcon = {
                    IconButton(onClick = {
                        navController.navigate("sales_dashboard") {
                            launchSingleTop = true
                            restoreState = true
                            popUpTo("sales_dashboard") { inclusive = false }
                        }
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        content = { innerPadding ->
            Box(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "User profile details here",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    )
}
