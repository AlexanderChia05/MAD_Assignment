package com.example.assignment.ui.theme.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.assignment.R
import com.example.assignment.viewmodel.LoginScreenViewModel
import com.example.assignment.viewmodel.SalesAgentViewModel

@Composable
fun SalesAgentScreen(
    navController: NavController,            // salesNav controller
    parentNavController: NavController,      // parent for logout only
    loginViewModel: LoginScreenViewModel,
    salesViewModel: SalesAgentViewModel,
    currentTab: MutableState<SalesTab>
) {
    val role by loginViewModel.role
    var showLogoutDialog by remember { mutableStateOf(false) }

    if (role != "sales") {
        LaunchedEffect(Unit) {
            parentNavController.navigate("login") {
                popUpTo("sales") { inclusive = true }
            }
        }
        return
    }

    val salesAgentId = loginViewModel.currentUser.value?.uid ?: ""
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState), // enable scrolling on rotation or small screens
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.lebron_james),
                    contentDescription = "User Photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(text = "Welcome", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Sales Agent", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
            }

            // My Profile
            ListItem(
                headlineContent = { Text("My Profile") },
                leadingContent = { Icon(Icons.Filled.Person, contentDescription = "My Profile Icon") },
                trailingContent = { Icon(Icons.Filled.ArrowForwardIos, contentDescription = "Go to Profile") },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        navController.navigate("agent_profile") {
                            launchSingleTop = true
                        }
                    }
                    .padding(vertical = 4.dp)
            )

            Divider()

            // Add New Order
            ListItem(
                headlineContent = { Text("Add New Order") },
                leadingContent = { Icon(Icons.Filled.AddShoppingCart, contentDescription = "Add New Order Icon") },
                trailingContent = { Icon(Icons.Filled.ArrowForwardIos, contentDescription = "Go to Add New Order") },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        currentTab.value = SalesTab.Add
                        navController.navigate("add_order?salesAgentId=$salesAgentId") {
                            launchSingleTop = true
                            popUpTo("sales_dashboard") { inclusive = false }
                        }
                    }
                    .padding(vertical = 4.dp)
            )

            Divider()

            // View Commission
            ListItem(
                headlineContent = { Text("View Commission") },
                leadingContent = { Icon(Icons.Filled.AttachMoney, contentDescription = "View Commission Icon") },
                trailingContent = { Icon(Icons.Filled.ArrowForwardIos, contentDescription = "Go to Commission") },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        currentTab.value = SalesTab.Commission
                        navController.navigate("commission?salesAgentId=$salesAgentId") {
                            launchSingleTop = true
                            popUpTo("sales_dashboard") { inclusive = false }
                        }
                    }
                    .padding(vertical = 4.dp)
            )

            Divider()

            // Show All Orders
            ListItem(
                headlineContent = { Text("Show All Orders") },
                leadingContent = { Icon(Icons.Filled.Checklist, contentDescription = "Show All Orders Icon") },
                trailingContent = { Icon(Icons.Filled.ArrowForwardIos, contentDescription = "Go to All Orders") },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        currentTab.value = SalesTab.Orders
                        navController.navigate("all_orders?salesAgentId=$salesAgentId") {
                            launchSingleTop = true
                            popUpTo("sales_dashboard") { inclusive = false }
                        }
                    }
                    .padding(vertical = 4.dp)
            )

            Divider()

            // Logout as a ListItem, aligned like the others
            ListItem(
                headlineContent = {
                    Text(
                        "Log Out",
                        color = MaterialTheme.colorScheme.error
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = "Logout Icon",
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                trailingContent = {
                    Icon(
                        imageVector = Icons.Filled.ArrowForwardIos,
                        contentDescription = "Logout",
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showLogoutDialog = true }
                    .padding(vertical = 4.dp)
            )
        }

        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                title = { Text("Confirm Logout") },
                text = { Text("Are you sure you want to log out?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showLogoutDialog = false
                            parentNavController.navigate("logout") {
                                popUpTo("sales") { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        modifier = Modifier.semantics { contentDescription = "Confirm Logout" }
                    ) { Text("Yes") }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showLogoutDialog = false },
                        modifier = Modifier.semantics { contentDescription = "Cancel Logout" }
                    ) { Text("No") }
                }
            )
        }
    }
}
