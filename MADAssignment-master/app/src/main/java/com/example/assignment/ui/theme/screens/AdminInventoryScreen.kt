package com.example.assignment.ui.theme.screens

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.assignment.viewmodel.AdminInventoryViewModel
import java.net.URLEncoder
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun AdminInventoryScreen(navController: NavHostController) {
    val viewModel: AdminInventoryViewModel = viewModel()
    val categoryRatios by viewModel.categoryRatios
    val categoriesWithProducts by viewModel.categoriesWithProducts
    val isLoading by viewModel.isLoading
    val errorMessage by viewModel.errorMessage

    // Build slices for Sales-style donut (quantity-based)
    val chartSlices = remember(categoryRatios) {
        val colors = categoryRatios.keys.map { generateSoftColor() }
        categoryRatios.entries.mapIndexed { idx, (category, qty) ->
            ChartSlice(category, qty.toDouble(), colors[idx])
        }
    }
    val totalQty = remember(categoryRatios) { categoryRatios.values.sum().roundToInt() }

    LaunchedEffect(Unit) { viewModel.fetchPurchasedItems() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Inventory Dashboard") }) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .padding(bottom = 120.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 100.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                    return@Column
                }

                errorMessage?.let { msg ->
                    Text(
                        text = msg,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                Text(
                    text = "Product Category Distribution",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                ElevatedCard(
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(30.dp)
                    ) {
                        DonutChart(
                            slices = chartSlices,
                            strokeWidth = 24.dp,
                            label = "Total",
                            valueLabel = totalQty.toString(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )

                        // Legend (same style as Sales dashboard)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val total = chartSlices.sumOf { it.value }.takeIf { it > 0 } ?: 1.0
                            chartSlices.forEach { slice ->
                                val pct = (slice.value * 100.0 / total)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(slice.color)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = slice.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${pct.roundToInt()}%",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }

                Text(
                    text = "Product Categories",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )

                val categoryList = categoriesWithProducts.keys.toList()

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categoryList.size) { idx ->
                        val category = categoryList[idx]
                        CategoryPill(
                            category = category,
                            count = categoriesWithProducts[category]?.size ?: 0,
                            onClick = {
                                val encoded = URLEncoder.encode(category.trim(), "UTF-8")
                                Log.d("CategoryPill", "Navigating to admin_batching?category=$encoded")
                                navController.navigate("admin_batching?category=$encoded")
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

/** Donut chart (same visuals as Sales screen) */
@Composable
private fun DonutChart(
    slices: List<ChartSlice>,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 32.dp,
    label: String = "",
    valueLabel: String = ""
) {
    val total = slices.sumOf { it.value }.takeIf { it > 0 } ?: 1.0
    val animatedSweep = remember { Animatable(0f) }

    LaunchedEffect(slices) {
        animatedSweep.snapTo(0f)
        animatedSweep.animateTo(1f, animationSpec = tween(durationMillis = 900))
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val diameter = size.minDimension
            val arcSize = Size(diameter, diameter)
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)

            var startAngle = -90f
            slices.forEach { slice ->
                val sliceAngleFloat: Float = ((slice.value / total) * 360.0).toFloat()
                val sweep: Float = sliceAngleFloat * animatedSweep.value
                drawArc(
                    color = slice.color,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = stroke,
                    size = arcSize,
                    topLeft = topLeft
                )
                startAngle += sliceAngleFloat
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (label.isNotEmpty()) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            if (valueLabel.isNotEmpty()) {
                Text(text = valueLabel, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Pill chip (unchanged) */
@Composable
fun CategoryPill(
    category: String,
    count: Int,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 2.dp,
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .wrapContentWidth()
            .heightIn(min = 56.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = category,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(8.dp))
            AssistChip(onClick = onClick, label = { Text("$count") })
        }
    }
}

/** Soft color helper (same aesthetic as Sales) */
private fun generateSoftColor(): Color {
    val r = (160..255).random() / 255f
    val g = (160..255).random() / 255f
    val b = (160..255).random() / 255f
    return Color(r, g, b)
}