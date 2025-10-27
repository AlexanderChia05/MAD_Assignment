package com.example.assignment.ui.theme.screens

import android.util.Log
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.assignment.data.SalesAgentHandle
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlin.math.roundToInt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommissionScreen(
    navController: NavController,
    salesAgentId: String,
    salesAgentHandle: SalesAgentHandle
) {
    // UI state
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var totalCommission by remember { mutableStateOf(0.0) }
    var commissionByCategory by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }

    // Last 5 purchases styled like AdminSalesDashboard "Last 5 Sales"
    var recentSales by remember { mutableStateOf<List<SalesRow>>(emptyList()) }

    // For re-triggering donut animation (e.g., on refresh)
    var resetTick by rememberSaveable { mutableStateOf(0) }

    // Commission policy
    val COMMISSION_RATE = 0.10 // 10%

    // Listen to purchases for this agent; map to categories & product meta via orders
    DisposableEffect(salesAgentId) {
        if (salesAgentId.isBlank()) {
            isLoading = false
            errorMessage = "No sales agent logged in."
            onDispose { }
        } else {
            val db = FirebaseFirestore.getInstance()
            var ordersListener: ListenerRegistration? = null
            var purchasesListener: ListenerRegistration? = null

            isLoading = true
            errorMessage = null

            // Map orderId -> meta for bucketing & display
            data class OrderMeta(val category: String, val productName: String, val quantity: Int)
            val orderMetaMap = mutableMapOf<String, OrderMeta>()

            ordersListener = db.collection("orders")
                .whereEqualTo("salesAgentId", salesAgentId)
                .addSnapshotListener { snap, e ->
                    if (e != null) {
                        Log.e("CommissionScreen", "Orders listen error: ${e.message}")
                        errorMessage = e.message
                        return@addSnapshotListener
                    }
                    orderMetaMap.clear()
                    snap?.documents.orEmpty().forEach { d ->
                        val cat = d.getString("category") ?: "Uncategorized"
                        val name = d.getString("productName") ?: "Unknown"
                        val qty = (d.getLong("quantity") ?: 0L).toInt()
                        orderMetaMap[d.id] = OrderMeta(cat, name, qty)
                    }
                }

            // Only count admin purchases (source of truth for commission)
            purchasesListener = db.collection("purchases")
                .whereEqualTo("salesAgentId", salesAgentId)
                .whereEqualTo("isAdminPurchase", true)
                .addSnapshotListener { snap, e ->
                    if (e != null) {
                        Log.e("CommissionScreen", "Purchases listen error: ${e.message}")
                        errorMessage = e.message
                        isLoading = false
                        return@addSnapshotListener
                    }

                    val perCat = linkedMapOf<String, Double>()
                    var total = 0.0

                    val rows = snap?.documents.orEmpty()
                        .map { d ->
                            val orderId = d.getString("orderId")
                            val meta = orderMetaMap[orderId]
                            val totalPrice = d.getDouble("totalPrice") ?: 0.0
                            val commission = totalPrice * COMMISSION_RATE
                            val ts = d.getLong("purchaseDate") ?: 0L
                            val purchaseId = d.id
                            val buyerName = d.getString("buyerName") ?: "" // optional

                            val category = meta?.category ?: "Uncategorized"
                            perCat[category] = (perCat[category] ?: 0.0) + commission
                            total += commission

                            SalesRow(
                                id = purchaseId,
                                productName = meta?.productName ?: "Unknown",
                                quantity = meta?.quantity ?: 0,
                                total = totalPrice,
                                commission = commission,
                                timestamp = ts,
                                buyerName = buyerName
                            )
                        }
                        .sortedByDescending { it.timestamp }
                        .take(5)

                    commissionByCategory = perCat.toMap()
                    totalCommission = total
                    recentSales = rows
                    isLoading = false
                    errorMessage = null

                    // re-animate donut whenever data set changes
                    resetTick++
                }

            onDispose {
                try { ordersListener?.remove() } catch (_: Throwable) {}
                try { purchasesListener?.remove() } catch (_: Throwable) {}
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Commission") },
                actions = {
                    TextButton(onClick = { resetTick++ }) { Text("Refresh") }
                }
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        val topInset = innerPadding.calculateTopPadding()
        // SCROLLABLE CONTENT
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topInset)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                isLoading -> {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                errorMessage != null -> {
                    Text(errorMessage ?: "", color = MaterialTheme.colorScheme.error)
                }
                else -> {
                    Text(
                        "Commission by Category",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Spacer(Modifier.height(20.dp))

                    if (commissionByCategory.isEmpty()) {
                        Text(
                            "No commissions yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        // Build donut slices with soft colors
                        val entries = commissionByCategory.entries.toList()
                        val colors = remember(entries) { entries.indices.map { generateSoftColor() } }
                        val slices = remember(entries) {
                            entries.mapIndexed { i, (name, value) ->
                                ChartSlice(name, value, colors[i]) // using the shared ChartSlice class
                            }
                        }

                        DonutChart(
                            slices = slices,
                            strokeWidth = 24.dp,
                            label = "Total",
                            valueLabel = "$" + "%.2f".format(slices.sumOf { it.value }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            resetKey = resetTick
                        )

                        Spacer(Modifier.height(20.dp))

                        // Legend: color • category • % • $amount
                        val total = slices.sumOf { it.value }.takeIf { it > 0 } ?: 1.0
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            slices.forEach { slice ->
                                val pct = (slice.value * 100.0 / total)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(slice.color)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = slice.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${pct.roundToInt()}%",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "$" + "%.2f".format(slice.value),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(18.dp))
                        Divider()
                        Spacer(Modifier.height(18.dp))

                        Text(
                            text = "Last 5 Sales",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                if (recentSales.isEmpty()) {
                                    Text(
                                        "No recent sales.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    recentSales.forEachIndexed { idx, tx ->
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
                                                val qtyLabel = if (tx.quantity > 0) " ×${tx.quantity}" else ""
                                                Text(
                                                    text = "${tx.productName}$qtyLabel",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                val parts = listOf(tx.buyerName, formatTs(tx.timestamp), tx.id)
                                                    .filter { it.isNotBlank() }
                                                Text(
                                                    text = parts.joinToString(" • "),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                                )
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    text = "Comm $" + "%.2f".format(tx.commission),
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = "Sold $" + "%.2f".format(tx.total),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                                )
                                            }
                                        }
                                        if (idx != recentSales.lastIndex) {
                                            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(120.dp))
                    }
                }
            }
        }
    }

}

/** Row model for the AdminSalesDashboard-style list */
private data class SalesRow(
    val id: String,
    val productName: String,
    val quantity: Int,
    val total: Double,
    val commission: Double,
    val timestamp: Long,
    val buyerName: String
)

/** Donut chart (same style/animation as AdminSalesDashboardScreen) */
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

            var startAngle = -90f
            slices.forEach { slice ->
                val sliceAngleFloat = ((slice.value / total) * 360.0).toFloat()
                val sweep = sliceAngleFloat * animatedSweep.value
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
                Text(
                    text = valueLabel,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** Helpers */
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
