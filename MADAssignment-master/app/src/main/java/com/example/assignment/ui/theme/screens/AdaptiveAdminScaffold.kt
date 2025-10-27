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
fun AdaptiveAdminScaffold(
    adminNav: NavHostController,
    currentAdminTab: MutableState<AdminTab>,
    content: @Composable (PaddingValues) -> Unit
) {
    BoxWithConstraints {
        val isLandscape = maxWidth > maxHeight
        val railWidth: Dp = 96.dp
        val systemInsets = WindowInsets.systemBars.asPaddingValues()

        if (isLandscape) {
            Row(Modifier.fillMaxSize()) {
                AdminLeftRail(
                    navController = adminNav,
                    currentAdminTab = currentAdminTab,
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
                                .widthIn(max = 820.dp)
                        ) {
                            content(innerPadding)
                        }
                    }
                }
            }
        } else {
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0)
            ) { innerPadding ->
                Box(Modifier.fillMaxSize()) {
                    // Screen content handles only top inset
                    content(innerPadding)

                    // Overlay floating tab bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                    ) {
                        AdminFloatingTabBar(
                            navController = adminNav,
                            currentAdminTab = currentAdminTab,
                            modifier = Modifier.navigationBarsPadding()
                        )
                    }
                }
            }
        }
    }
}
