package com.example.assignment

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.assignment.ui.theme.AssignmentTheme
import com.example.assignment.ui.theme.screens.*
import com.example.assignment.viewmodel.LoginScreenViewModel
import com.example.assignment.viewmodel.SalesAgentViewModel
import com.example.assignment.data.LoginScreenHandle
import com.example.assignment.data.SalesAgentHandle
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {

    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        try {
            FirebaseApp.initializeApp(this)
            db = FirebaseFirestore.getInstance()
            Log.d("MainActivity", "Firebase and Firestore initialized successfully")
        } catch (e: Exception) {
            Log.e("MainActivity", "Firebase initialization failed", e)
        }

        setContent {
            AssignmentTheme {
                val loginFactory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        if (modelClass.isAssignableFrom(LoginScreenViewModel::class.java)) {
                            val loginHandle = LoginScreenHandle()
                            return LoginScreenViewModel(loginHandle) as T
                        }
                        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                    }
                }
                val salesFactory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        if (modelClass.isAssignableFrom(SalesAgentViewModel::class.java)) {
                            val salesAgentHandle = SalesAgentHandle()
                            return SalesAgentViewModel(salesAgentHandle) as T
                        }
                        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                    }
                }

                val loginViewModel: LoginScreenViewModel = viewModel(factory = loginFactory)
                val salesViewModel: SalesAgentViewModel = viewModel(factory = salesFactory)

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppNavigation(
                        navController = rememberNavController(),
                        loginViewModel = loginViewModel,
                        salesViewModel = salesViewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    loginViewModel: LoginScreenViewModel,
    salesViewModel: SalesAgentViewModel,
    modifier: Modifier = Modifier
) {
    val currentTab = remember { mutableStateOf(SalesTab.Home) }

    NavHost(navController = navController, startDestination = "login", modifier = modifier) {
        composable("login") { LoginScreen(navController, loginViewModel) }
        composable("register") { RegisterScreen(navController, loginViewModel) }

        composable("sales") {
            SalesShell(
                parentNav = navController,
                loginViewModel = loginViewModel,
                salesViewModel = salesViewModel,
                currentTab = currentTab
            )
        }

        composable("admin_area") {
            AdminAgentScreen(parentNav = navController, loginViewModel = loginViewModel)
        }
        composable("logout") { LogoutScreen(navController, loginViewModel) }
    }
}

