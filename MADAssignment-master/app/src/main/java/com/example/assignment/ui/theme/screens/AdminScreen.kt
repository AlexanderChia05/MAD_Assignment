package com.example.assignment.ui.theme.screens

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.assignment.viewmodel.LoginScreenViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.max

/* ===========================
   ViewModel: aggregates per-category COST & REVENUE
   =========================== */

private const val PURCHASES_COLLECTION = "purchases"
private const val SALES_COLLECTION = "sales"

class AdminOverviewViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val vmJob = Job()
    private val io = CoroutineScope(Dispatchers.IO + vmJob)

    private var salesListener: ListenerRegistration? = null
    private var purchasesListener: ListenerRegistration? = null

    private val _costByCategory =
        MutableStateFlow(savedStateHandle.get<Map<String, Double>>("overview_costByCat") ?: emptyMap())
    val costByCategory: StateFlow<Map<String, Double>> = _costByCategory

    private val _revenueByCategory =
        MutableStateFlow(savedStateHandle.get<Map<String, Double>>("overview_revByCat") ?: emptyMap())
    val revenueByCategory: StateFlow<Map<String, Double>> = _revenueByCategory

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        attachPurchases()
        attachSales()
    }

    private fun attachPurchases() {
        if (purchasesListener != null) return
        _loading.value = true

        purchasesListener = firestore.collection(PURCHASES_COLLECTION)
            .orderBy("category", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, err ->
                if (err != null) {
                    _error.value = "Failed to load purchases: ${err.message}"
                    _loading.value = false
                    return@addSnapshotListener
                }
                val docs = snapshot?.documents.orEmpty()

                io.launch {
                    val agg = linkedMapOf<String, Double>()
                    for (d in docs) {
                        val cat = d.getString("category")?.ifBlank { "Uncategorized" } ?: "Uncategorized"
                        val totalPrice = when (val v = d.get("totalPrice")) {
                            is Number -> v.toDouble()
                            is String -> v.toDoubleOrNull() ?: 0.0
                            else -> null
                        } ?: run {
                            val price = when (val p = d.get("price")) {
                                is Number -> p.toDouble()
                                is String -> p.toDoubleOrNull() ?: 0.0
                                else -> 0.0
                            }
                            val qty = when (val q = d.get("quantity")) {
                                is Number -> q.toInt()
                                is String -> q.toIntOrNull() ?: 0
                                else -> 0
                            }
                            price * qty.toDouble()
                        }
                        agg[cat] = (agg[cat] ?: 0.0) + totalPrice
                    }
                    _costByCategory.value = agg
                    savedStateHandle["overview_costByCat"] = agg
                    _loading.value = false
                }
            }
    }

    private fun attachSales() {
        if (salesListener != null) return
        _loading.value = true

        salesListener = firestore.collection(SALES_COLLECTION)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, err ->
                if (err != null) {
                    _error.value = "Failed to load sales: ${err.message}"
                    _loading.value = false
                    return@addSnapshotListener
                }
                val docs = snapshot?.documents.orEmpty()

                io.launch {
                    val agg = linkedMapOf<String, Double>()
                    for (d in docs) {
                        val cat = d.getString("category")?.ifBlank { "Uncategorized" } ?: "Uncategorized"
                        val total = when (val t = d.get("total")) {
                            is Number -> t.toDouble()
                            is String -> t.toDoubleOrNull() ?: 0.0
                            else -> null
                        } ?: run {
                            val unit = when (val u = d.get("unitPrice")) {
                                is Number -> u.toDouble()
                                is String -> u.toDoubleOrNull() ?: 0.0
                                else -> 0.0
                            }
                            val qty = when (val q = d.get("quantity")) {
                                is Number -> q.toInt()
                                is String -> q.toIntOrNull() ?: 0
                                else -> 0
                            }
                            unit * qty.toDouble()
                        }
                        agg[cat] = (agg[cat] ?: 0.0) + total
                    }
                    _revenueByCategory.value = agg
                    savedStateHandle["overview_revByCat"] = agg
                    _loading.value = false
                }
            }
    }

    fun resetOverview() {
        purchasesListener?.remove(); purchasesListener = null
        salesListener?.remove(); salesListener = null
        _costByCategory.value = emptyMap()
        _revenueByCategory.value = emptyMap()
        _error.value = null
        savedStateHandle["overview_costByCat"] = emptyMap<String, Double>()
        savedStateHandle["overview_revByCat"] = emptyMap<String, Double>()
    }

    fun resumeOverview() {
        attachPurchases()
        attachSales()
    }

    override fun onCleared() {
        super.onCleared()
        purchasesListener?.remove()
        salesListener?.remove()
        io.cancel()
    }
}

/* ===========================
   AdminScreen aligned to AdminInventoryScreen:
   - Uses Scaffold with TopAppBar
   - Scrollable content
   - Bottom floating Tab Bar aligned BottomCenter
   - Grouped bar + two donut charts row
   =========================== */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    navController: NavHostController,
    loginViewModel: LoginScreenViewModel,
    parentNav: androidx.navigation.NavController
) {
    val vm: AdminOverviewViewModel = viewModel()

    var showLogoutDialog by remember { mutableStateOf(false) }
    val costByCat by vm.costByCategory.collectAsState()
    val revByCat by vm.revenueByCategory.collectAsState()
    val loading by vm.loading.collectAsState()
    val errorMsg by vm.error.collectAsState()

    val categories = remember(costByCat, revByCat) {
        (costByCat.keys + revByCat.keys).toSortedSet().toList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Dashboard") },
                actions = {
                    TextButton(onClick = { vm.resetOverview() }) { Text("Reset") }
                    TextButton(onClick = { vm.resumeOverview() }) { Text("Reload") }
                }
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = innerPadding.calculateTopPadding())
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .padding(bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // Grouped Bar Chart Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            text = "Cost vs Revenue by Category",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        if (loading && categories.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp),
                                contentAlignment = Alignment.Center
                            ) { CircularProgressIndicator() }
                        } else if (errorMsg != null && categories.isEmpty()) {
                            Text(
                                text = errorMsg ?: "Unknown error",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        } else if (categories.isEmpty()) {
                            Text("No data available.", modifier = Modifier.padding(top = 12.dp))
                        } else {
                            Spacer(Modifier.height(8.dp))
                            UniformSlotGroupedBarChart(
                                categories = categories,
                                cost = { cat -> costByCat[cat] ?: 0.0 },
                                revenue = { cat -> revByCat[cat] ?: 0.0 },
                                maxHeight = 220.dp,
                                slotPadding = 10.dp,     // inner padding inside each category slot
                                barWidth = 18.dp,        // width per bar (auto-scales on small screens)
                                barGapInGroup = 10.dp,   // fixed gap between cost|revenue (also scales)
                                costColor = MaterialTheme.colorScheme.tertiary,
                                revenueColor = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(12.dp))

                            // Legend
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                LegendChip(color = MaterialTheme.colorScheme.tertiary, label = "Cost")
                                LegendChip(color = MaterialTheme.colorScheme.primary, label = "Revenue")
                            }
                        }
                    }
                }

                // ===== Row of two pie charts (Inventory/Cost + Sales/Revenue) =====
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Cost Distribution (Inventory-style)
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Cost Distribution",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )

                            val invSlices = remember(costByCat) {
                                val colors = softPalette(costByCat.size)
                                costByCat.entries.mapIndexed { i, (cat, value) ->
                                    AdminChartSlice(cat, value, colors[i])
                                }
                            }

                            AdminDonutChart(
                                slices = invSlices,
                                strokeWidth = 15.dp,
                                label = "Total",
                                valueLabel = "$" + "%.2f".format(invSlices.sumOf { it.value }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                            )
                        }
                    }

                    // Sales Distribution (Revenue)
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Sales Distribution",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )

                            val salesSlices = remember(revByCat) {
                                val colors = softPalette(revByCat.size)
                                revByCat.entries.mapIndexed { i, (cat, value) ->
                                    AdminChartSlice(cat, value, colors[i])
                                }
                            }

                            AdminDonutChart(
                                slices = salesSlices,
                                strokeWidth = 15.dp,
                                label = "Total",
                                valueLabel = "$" + "%.2f".format(salesSlices.sumOf { it.value }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                            )
                        }
                    }
                }

                // Logout button (kept)
                OutlinedButton(
                    onClick = { showLogoutDialog = true },
                    modifier = Modifier
                        .width(200.dp)
                        .height(36.dp)
                        .semantics { contentDescription = "Logout button" },
                    border = BorderStroke(1.dp, Color.Red),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White,
                        contentColor = Color.Red
                    ),
                ) { Text("Logout") }

                if (showLogoutDialog) {
                    AlertDialog(
                        onDismissRequest = {
                            Log.d("AdminScreen", "Logout dialog dismissed")
                            showLogoutDialog = false
                        },
                        title = { Text("Confirm Logout") },
                        text = { Text("Are you sure you want to log out?") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    Log.d("AdminScreen", "Logout confirmed, navigating to logout")
                                    showLogoutDialog = false
                                    parentNav.navigate("logout") {
                                        popUpTo("login") { inclusive = false }
                                    }
                                },
                                modifier = Modifier.semantics { contentDescription = "Confirm Logout" }
                            ) { Text("Yes") }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    Log.d("AdminScreen", "Logout cancelled")
                                    showLogoutDialog = false
                                },
                                modifier = Modifier.semantics { contentDescription = "Cancel Logout" }
                            ) { Text("No") }
                        }
                    )
                }
            }
        }
    }
}

/* ===========================
   UI pieces
   =========================== */

@Composable
private fun LegendChip(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(14.dp, 14.dp)
                .background(color = color, shape = MaterialTheme.shapes.small)
        )
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * Grouped bar chart with UNIFORM category slots and phone-safe scaling:
 *  - Canvas width is divided into N equal slots (N = categories.size).
 *  - Inside each slot, we center two fixed-width bars (Cost, Revenue) with a fixed gap between them.
 *  - If the pair doesn’t fit the slot on small screens, bars and gap are scaled down uniformly.
 */
@Composable
private fun UniformSlotGroupedBarChart(
    categories: List<String>,
    cost: (String) -> Double,
    revenue: (String) -> Double,
    maxHeight: Dp,
    slotPadding: Dp,
    barWidth: Dp,
    barGapInGroup: Dp,
    costColor: Color,
    revenueColor: Color
) {
    val values = remember(categories) { categories.map { cat -> cost(cat) to revenue(cat) } }
    val maxVal = remember(values) { values.maxOfOrNull { max(it.first, it.second) }?.takeIf { it > 0.0 } ?: 1.0 }

    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val axisColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    val labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(maxHeight) // room for x labels
            .padding(horizontal = 8.dp)
    ) {
        val chartTop = 8.dp.toPx()
        val chartBottom = size.height - 28.dp.toPx()
        val chartHeight = chartBottom - chartTop

        fun toY(v: Double): Float {
            val p = (v / maxVal).toFloat().coerceIn(0f, 1f)
            return chartBottom - (p * chartHeight)
        }

        // dashed grid
        val gridSteps = 3
        val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
        for (i in 0..gridSteps) {
            val y = chartTop + (chartHeight * i / gridSteps)
            drawLine(
                color = gridColor,
                start = androidx.compose.ui.geometry.Offset(0f, y),
                end = androidx.compose.ui.geometry.Offset(size.width, y),
                strokeWidth = 1f,
                pathEffect = dash
            )
        }

        // axes
        drawLine(
            color = axisColor,
            start = androidx.compose.ui.geometry.Offset(0f, chartTop),
            end = androidx.compose.ui.geometry.Offset(0f, chartBottom),
            strokeWidth = 2f
        )
        drawLine(
            color = axisColor,
            start = androidx.compose.ui.geometry.Offset(0f, chartBottom),
            end = androidx.compose.ui.geometry.Offset(size.width, chartBottom),
            strokeWidth = 2f
        )

        val n = categories.size
        if (n == 0) return@Canvas

        // Per-slot geometry
        val slotWidth = size.width / n
        val innerPad = slotPadding.toPx()

        // Desired group geometry
        val bw = barWidth.toPx()
        val gap = barGapInGroup.toPx()
        val desiredGroup = (bw * 2f) + gap

        // Available horizontal size inside a slot
        val available = (slotWidth - innerPad * 2f).coerceAtLeast(0f)

        // Scale bars+gap down if they don't fit in the slot on small screens
        val scale = (available / desiredGroup).coerceAtMost(1f)
        val bwScaled = bw * scale
        val gapScaled = gap * scale
        val groupScaled = (bwScaled * 2f) + gapScaled

        // Center the (bar+gap+bar) inside the slot
        val offsetWithinSlot = ((slotWidth - (groupScaled + innerPad * 2f)) / 2f).coerceAtLeast(0f)

        // Adaptive label size and trimming for tiny slots
        val labelTextSizePx = (minOf(10.dp.toPx(), slotWidth / 8f)).coerceAtLeast(7.dp.toPx())
        val maxChars = when {
            slotWidth < 60f -> 4
            slotWidth < 80f -> 6
            else -> 10
        }

        categories.forEachIndexed { index, cat ->
            val slotStartX = slotWidth * index
            val contentStartX = slotStartX + innerPad + offsetWithinSlot

            val leftBarLeft = contentStartX
            val leftBarRight = leftBarLeft + bwScaled
            val rightBarLeft = leftBarRight + gapScaled
            val rightBarRight = rightBarLeft + bwScaled

            val (cVal, rVal) = values[index]
            val cTop = toY(cVal)
            val rTop = toY(rVal)

            // cost bar
            drawRect(
                color = costColor,
                topLeft = androidx.compose.ui.geometry.Offset(leftBarLeft, cTop),
                size = androidx.compose.ui.geometry.Size(leftBarRight - leftBarLeft, chartBottom - cTop)
            )
            // revenue bar
            drawRect(
                color = revenueColor,
                topLeft = androidx.compose.ui.geometry.Offset(rightBarLeft, rTop),
                size = androidx.compose.ui.geometry.Size(rightBarRight - rightBarLeft, chartBottom - rTop)
            )

            // category label centered under the pair
            val label = if (cat.length > maxChars) cat.take(maxChars) + "…" else cat
            drawIntoCanvas { canvas ->
                val paint = androidx.compose.ui.graphics.Paint().asFrameworkPaint().apply {
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                    textSize = labelTextSizePx
                    color = labelColor.toArgb()
                }
                val centerX = (leftBarLeft + rightBarRight) / 2f
                canvas.nativeCanvas.drawText(
                    label,
                    centerX,
                    chartBottom + 16.dp.toPx(),
                    paint
                )
            }
        }
    }
}

/* ===========================
   Local Pie Chart (no cross-file name clashes)
   =========================== */

private data class AdminChartSlice(val name: String, val value: Double, val color: Color)

@Composable
private fun AdminDonutChart(
    slices: List<AdminChartSlice>,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 32.dp,
    label: String = "",
    valueLabel: String = ""
) {
    val total = slices.sumOf { it.value }.takeIf { it > 0 } ?: 1.0
    val animatedSweep = remember(slices) { androidx.compose.animation.core.Animatable(0f) }

    LaunchedEffect(slices) {
        animatedSweep.snapTo(0f)
        animatedSweep.animateTo(
            1f,
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 900)
        )
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val diameter = size.minDimension
            val arcSize = androidx.compose.ui.geometry.Size(diameter, diameter)
            val topLeft = androidx.compose.ui.geometry.Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f
            )
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
                Text(
                    text = valueLabel,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun softPalette(n: Int): List<Color> {
    if (n <= 0) return emptyList()
    val base = listOf(
        Color(0xFFB3E5FC), // light blue
        Color(0xFFFFF9C4), // light yellow
        Color(0xFFC8E6C9), // light green
        Color(0xFFFFCCBC), // light orange
        Color(0xFFD1C4E9), // light purple
        Color(0xFFFFCDD2), // light red
        Color(0xFFB2DFDB), // teal
        Color(0xFFE1BEE7)  // purple
    )
    if (n <= base.size) return base.take(n)
    val list = mutableListOf<Color>()
    var i = 0
    repeat(n) {
        list += base[i % base.size]
        i++
    }
    return list
}
