package com.example.assignment.ui.theme.screens

import android.annotation.SuppressLint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.livedata.observeAsState
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
import kotlin.math.roundToInt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminSalesDashboardScreen(
    navController: NavHostController,
    viewModel: AdminSalesDashboardViewModel = viewModel()
) {
    // Sales VM data
    val categoryRevenueVm by viewModel.categoryRevenue.observeAsState(emptyList())
    val topProductsVm by viewModel.topProducts.observeAsState(emptyList())
    val recentTransactionsVm by viewModel.recentTransactions.observeAsState(emptyList())

    // Overview VM (to reuse the same Cost aggregation used in AdminScreen)
    val overviewVm: AdminOverviewViewModel = viewModel()
    val costByCat by overviewVm.costByCategory.collectAsState()

    // Reset button should re-trigger chart animation without changing business logic
    var resetTick by rememberSaveable { mutableStateOf(0) }

    // Toggle between Revenue and Cost donut
    var showCost by rememberSaveable { mutableStateOf(false) }

    val categoryRevenueFallback = listOf(
        CategoryRevenue("Empty", 0.0),
    )
    val topProductsFallback = listOf(
        TopProduct("Empty", 0, 0.0),
    )
    val recentTxFallback = listOf(
        SalesTransaction("Empty Transaction ID", "Empty", 0, 0.0, System.currentTimeMillis(), " "),
    )

    val categoryRevenueData: List<CategoryRevenue> =
        if (categoryRevenueVm.isNotEmpty()) categoryRevenueVm else categoryRevenueFallback

    val topProductsData: List<TopProduct> =
        if (topProductsVm.isNotEmpty()) topProductsVm else topProductsFallback

    val recentTransactionsData: List<SalesTransaction> =
        if (recentTransactionsVm.isNotEmpty()) recentTransactionsVm.take(5) else recentTxFallback.take(5)

    // Revenue slices from Sales VM
    val revenueSlices = remember(categoryRevenueData) {
        val colors = categoryRevenueData.indices.map { generateSoftColor() }
        categoryRevenueData.mapIndexed { index, item ->
            ChartSlice(item.category, item.revenue, colors[index])
        }
    }

    // Cost slices from Overview VM (same logic/palette style as AdminScreen)
    val costSlices = remember(costByCat) {
        val entries = costByCat.entries.toList()
        val colors = entries.indices.map { generateSoftColor() }
        entries.mapIndexed { i, (cat, value) ->
            ChartSlice(cat, value, colors[i])
        }
    }

    // Pick which dataset to display
    val activeTitle = if (showCost) "Cost by Category" else "Revenue by Category"
    val activeSlices = if (showCost) costSlices else revenueSlices
    val activeTotalLabel = (if (showCost) "$" else "$") + "%.2f".format(activeSlices.sumOf { it.value })

    Surface {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Sales Dashboard") },
                    actions = {
                        TextButton(onClick = {
                            // keep existing reset call
                            viewModel.resetDashboard()
                            // also re-animate the donut
                            resetTick++
                        }) { Text("Reset") }
                    }
                )
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { innerPadding ->
            Box(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(top = innerPadding.calculateTopPadding())
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .padding(bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {

                    // ======= Header row with right-aligned switch (Revenue <-> Cost) =======
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = activeTitle, style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Cost",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Switch(
                                checked = showCost,
                                onCheckedChange = {
                                    showCost = it
                                    // re-run animation when switching
                                    resetTick++
                                }
                            )
                        }
                    }

                    // ======= Donut + per-slice legend =======
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
                                slices = activeSlices,
                                strokeWidth = 24.dp,
                                label = "Total",
                                valueLabel = activeTotalLabel,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                resetKey = resetTick
                            )

                            // Category legend (same style as your other screens)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val total = activeSlices.sumOf { it.value }.takeIf { it > 0 } ?: 1.0
                                activeSlices.forEach { slice ->
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

                    // ======= Top products =======
                    Text(
                        text = "Top 5 Best-Selling Products (This Week)",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            topProductsData.take(5).forEachIndexed { idx, p ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RankBadge(rank = idx + 1)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = p.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "${p.units} sold",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        )
                                    }
                                    Text(
                                        text = "$" + "%.2f".format(p.revenue),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (idx != 4) Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                            }
                        }
                    }

                    // ======= Recent sales =======
                    Text(text = "Last 5 Sales", style = MaterialTheme.typography.titleMedium)

                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            recentTransactionsData.forEachIndexed { idx, tx ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = "${tx.productName}  ×${tx.quantity}",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "${tx.buyerName} • ${formatTs(tx.timestamp)} • ${tx.id}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        )
                                    }
                                    Text(
                                        text = "$" + "%.2f".format(tx.total),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (idx != recentTransactionsData.lastIndex) {
                                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** ViewModel data models */
data class CategoryRevenue(val category: String, val revenue: Double)
data class SalesTransaction(
    val id: String,
    val productName: String,
    val quantity: Int,
    val total: Double,
    val timestamp: Long,
    val buyerName: String
)

/** Donut chart with resetKey to reanimate */
@Composable
private fun DonutChart(
    slices: List<ChartSlice>,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 32.dp,
    label: String = "",
    valueLabel: String = "",
    resetKey: Int = 0
) {
    val total = slices.sumOf { it.value }.takeIf { it > 0 } ?: 1.0
    val animatedSweep = remember { Animatable(0f) }

    LaunchedEffect(slices, resetKey) {
        animatedSweep.snapTo(0f)
        animatedSweep.animateTo(1f, animationSpec = tween(durationMillis = 900))
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val diameter = size.minDimension
            val arcSize = Size(diameter, diameter)
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)

            var startAngle: Float = -90f
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

/** Helpers */
data class TopProduct(val name: String, val units: Int, val revenue: Double)

/**
 * Provided elsewhere in your project:
 * data class ChartSlice(val name: String, val value: Double, val color: Color)
 */
private fun generateSoftColor(): Color {
    val r = (160..255).random() / 255f
    val g = (160..255).random() / 255f
    val b = (160..255).random() / 255f
    return Color(r, g, b)
}

private fun formatTs(tsMillis: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    sdf.timeZone = TimeZone.getTimeZone("GMT+8")
    return sdf.format(Date(tsMillis))
}

@Composable
private fun RankBadge(rank: Int) {
    val bg = when (rank) {
        1 -> Color(0xFFFFD700)
        2 -> Color(0xFFC0C0C0)
        3 -> Color(0xFFCD7F32)
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = rank.toString(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (rank <= 3) Color.Black else MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}
