package com.example.assignment.ui.theme.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.asPaddingValues

@Composable
fun AdaptiveSalesScaffold(
    salesNav: NavHostController,
    salesAgentId: String,
    currentTab: MutableState<SalesTab>,
    content: @Composable (PaddingValues) -> Unit
) {
    BoxWithConstraints {
        val isLandscape = maxWidth > maxHeight
        val railWidth: Dp = 96.dp
        val systemInsets = WindowInsets.systemBars.asPaddingValues()

        if (isLandscape) {
            Row(Modifier.fillMaxSize()) {
                SalesLeftRail(
                    navController = salesNav,
                    salesAgentId = salesAgentId,
                    currentTab = currentTab,
                    modifier = Modifier
                        .width(railWidth)
                        .fillMaxHeight()
                )
                Scaffold(
                    bottomBar = {},
                    contentWindowInsets = WindowInsets(0, 0, 0, 0)
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .widthIn(max = 680.dp)
                        ) {
                            content(innerPadding)
                        }
                    }
                }
            }
        } else {
            // IMPORTANT: no bottomBar here. We overlay it ourselves so content can scroll behind.
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0)
            ) { innerPadding ->
                Box(
                    Modifier
                        .fillMaxSize()
                    // Do NOT pad by innerPadding here; let screens handle top-only padding.
                ) {
                    // Screen content (will use top-only padding in each screen)
                    content(innerPadding)

                    // Floating Tab Bar overlay (like AdminSalesDashboard)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                    ) {
                        SalesFloatingTabBar(
                            navController = salesNav,
                            salesAgentId = salesAgentId,
                            currentTab = currentTab,
                            modifier = Modifier
                                .navigationBarsPadding()
                        )
                    }
                }
            }
        }
    }
}
