package com.example.assignment.ui.theme.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.assignment.R

@Composable
fun SalesFloatingTabBar(
    navController: NavController,
    salesAgentId: String,
    currentTab: MutableState<SalesTab>,
    modifier: Modifier = Modifier
) {
    val items = listOf(SalesTab.Home, SalesTab.Add, SalesTab.Commission, SalesTab.Orders)
    val current = currentTab.value
    Surface(
        tonalElevation = 6.dp,
        shadowElevation = 10.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shape = RoundedCornerShape(26.dp),
        modifier = modifier
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .navigationBarsPadding()
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                items.forEachIndexed { index, tab ->
                    SegmentedButton(
                        selected = current == tab,
                        onClick = {
                            if (current != tab) {
                                currentTab.value = tab
                                when (tab) {
                                    SalesTab.Home -> navController.navigate("sales_dashboard") {
                                        popUpTo("sales_dashboard") { inclusive = false }
                                        launchSingleTop = true
                                    }
                                    SalesTab.Add -> navController.navigate("add_order?salesAgentId=$salesAgentId") {
                                        launchSingleTop = true
                                    }
                                    SalesTab.Commission -> navController.navigate("commission?salesAgentId=$salesAgentId") {
                                        launchSingleTop = true
                                    }
                                    SalesTab.Orders -> navController.navigate("all_orders?salesAgentId=$salesAgentId") {
                                        launchSingleTop = true
                                    }
                                }
                            }
                        },
                        border = BorderStroke(0.dp, Color.Transparent),
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = items.size)
                    ) {
                        when (tab) {
                            SalesTab.Home -> Icon(
                                painter = painterResource(id = R.drawable.baseline_home_24),
                                contentDescription = "Home"
                            )
                            SalesTab.Add -> Icon(
                                painter = painterResource(id = R.drawable.outline_add_shopping_cart_24),
                                contentDescription = "Add Order"
                            )
                            SalesTab.Commission -> Icon(
                                painter = painterResource(id = R.drawable.outline_attach_money_24),
                                contentDescription = "Commission"
                            )
                            SalesTab.Orders -> Icon(
                                painter = painterResource(id = R.drawable.outline_checklist_rtl_24),
                                contentDescription = "All Orders"
                            )
                        }
                    }
                }
            }
        }
    }
}
