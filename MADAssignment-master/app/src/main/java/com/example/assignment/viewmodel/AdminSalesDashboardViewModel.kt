package com.example.assignment.ui.theme.screens

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
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

/**
 * ViewModel for Admin Sales Dashboard.
 *
 * Exposes:
 *  - categoryRevenue: LiveData<List<CategoryRevenue>>
 *  - topProducts: LiveData<List<TopProduct>>
 *  - recentTransactions: LiveData<List<SalesTransaction>>
 *
 * Persists small keyed UI states (survive rotation) via SavedStateHandle:
 *  - sales_metricTab: String ("REVENUE" by default)
 *  - sales_dateRange: String ("THIS_MONTH" by default)
 *  - sales_recentLimit: Int (5 by default)
 *  - sales_lastUpdated: Long (timestamp of last snapshot apply)
 *
 * Firestore source: "sales"
 * Each sales doc expected fields:
 *   productName:String
 *   category:String
 *   unitPrice:Number/String
 *   quantity:Number/String
 *   timestamp:Number/String (epoch millis)
 *   buyerName:String
 *
 * Revenue = unitPrice * quantity
 */
class AdminSalesDashboardViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val firestore by lazy { FirebaseFirestore.getInstance() }

    // ---------- Keys ----------
    private val KEY_METRIC_TAB = "sales_metricTab"
    private val KEY_DATE_RANGE = "sales_dateRange"
    private val KEY_RECENT_LIMIT = "sales_recentLimit"
    private val KEY_LAST_UPDATED = "sales_lastUpdated"

    // ---------- Persisted small UI states ----------
    private val _metricTab =
        MutableStateFlow(savedStateHandle.get<String>(KEY_METRIC_TAB) ?: "REVENUE")
    val metricTab: StateFlow<String> = _metricTab

    private val _dateRange =
        MutableStateFlow(savedStateHandle.get<String>(KEY_DATE_RANGE) ?: "THIS_MONTH")
    val dateRange: StateFlow<String> = _dateRange

    private val _recentLimit =
        MutableStateFlow(savedStateHandle.get<Int>(KEY_RECENT_LIMIT) ?: 5)
    val recentLimit: StateFlow<Int> = _recentLimit

    private val _lastUpdated =
        MutableStateFlow(savedStateHandle.get<Long>(KEY_LAST_UPDATED) ?: 0L)
    val lastUpdated: StateFlow<Long> = _lastUpdated

    fun setMetricTab(value: String) {
        _metricTab.value = value
        savedStateHandle[KEY_METRIC_TAB] = value
    }

    fun setDateRange(value: String) {
        _dateRange.value = value
        savedStateHandle[KEY_DATE_RANGE] = value
    }

    fun setRecentLimit(value: Int) {
        val v = max(1, value)
        _recentLimit.value = v
        savedStateHandle[KEY_RECENT_LIMIT] = v
        // Optionally re-emit recent transactions using the new limit:
        // (No network needed; we’ll re-slice the next snapshot. If you want instant effect,
        // call forceRecomputeFromCache() here with a cached list.)
    }

    // ---------- Exposed dashboard data ----------
    private val _categoryRevenue = MutableLiveData<List<CategoryRevenue>>(emptyList())
    val categoryRevenue: LiveData<List<CategoryRevenue>> = _categoryRevenue

    private val _topProducts = MutableLiveData<List<TopProduct>>(emptyList())
    val topProducts: LiveData<List<TopProduct>> = _topProducts

    private val _recentTransactions = MutableLiveData<List<SalesTransaction>>(emptyList())
    val recentTransactions: LiveData<List<SalesTransaction>> = _recentTransactions

    // ---------- Internal ----------
    private var listener: ListenerRegistration? = null
    private val vmJob = Job()
    private val ioScope = CoroutineScope(Dispatchers.IO + vmJob)

    init {
        attachListener()
    }

    /** Attach the Firestore real-time listener if not already attached. */
    private fun attachListener() {
        if (listener != null) return

        listener = firestore.collection("sales")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Optionally log error
                    return@addSnapshotListener
                }
                val docs = snapshot?.documents ?: emptyList()

                ioScope.launch {
                    val parsed = docs.mapNotNull { doc ->
                        val id = doc.id
                        val productName = doc.getString("productName") ?: return@mapNotNull null
                        val category = doc.getString("category") ?: "Uncategorized"

                        val unitPriceNum = doc.get("unitPrice")
                        val unitPrice = when (unitPriceNum) {
                            is Number -> unitPriceNum.toDouble()
                            is String -> unitPriceNum.toDoubleOrNull() ?: 0.0
                            else -> 0.0
                        }

                        val qtyNum = doc.get("quantity")
                        val quantity = when (qtyNum) {
                            is Number -> qtyNum.toInt()
                            is String -> qtyNum.toIntOrNull() ?: 0
                            else -> 0
                        }

                        val tsNum = doc.get("timestamp")
                        val timestamp = when (tsNum) {
                            is Number -> tsNum.toLong()
                            is String -> tsNum.toLongOrNull() ?: 0L
                            else -> 0L
                        }

                        val buyerName = doc.getString("buyerName") ?: "Unknown"
                        val total = unitPrice * quantity.toDouble()

                        RawSale(
                            id = id,
                            productName = productName,
                            category = category,
                            unitPrice = unitPrice,
                            quantity = quantity,
                            total = total,
                            timestamp = timestamp,
                            buyerName = buyerName
                        )
                    }

                    // ---- Aggregations ----
                    val categoryAgg = aggregateCategoryRevenue(parsed)
                    val topProductsAgg = aggregateTopProducts(parsed)

                    val limit = max(1, _recentLimit.value)
                    val recent = parsed.sortedByDescending { it.timestamp }
                        .take(limit)
                        .map {
                            SalesTransaction(
                                id = it.id,
                                productName = it.productName,
                                quantity = it.quantity,
                                total = it.total,
                                timestamp = it.timestamp,
                                buyerName = it.buyerName
                            )
                        }

                    // Emit to UI
                    _categoryRevenue.postValue(categoryAgg)
                    _topProducts.postValue(topProductsAgg)
                    _recentTransactions.postValue(recent)

                    // Persist "last updated"
                    val now = System.currentTimeMillis()
                    _lastUpdated.value = now
                    savedStateHandle[KEY_LAST_UPDATED] = now
                }
            }
    }

    /** Clear current dashboard data and detach the Firestore listener. */
    fun resetDashboard() {
        listener?.remove()
        listener = null
        _categoryRevenue.value = emptyList()
        _topProducts.value = emptyList()
        _recentTransactions.value = emptyList()
        _lastUpdated.value = 0L
        savedStateHandle[KEY_LAST_UPDATED] = 0L
    }

    /** Re-attach the Firestore listener (e.g., after a reset) */
    fun resumeDashboard() {
        attachListener()
    }

    // ---- Helpers ----
    private fun aggregateCategoryRevenue(sales: List<RawSale>): List<CategoryRevenue> {
        if (sales.isEmpty()) return emptyList()
        val byCategory = linkedMapOf<String, Double>()
        sales.forEach { s ->
            val cur = byCategory[s.category] ?: 0.0
            byCategory[s.category] = cur + s.total
        }
        return byCategory.entries
            .sortedByDescending { it.value }
            .map { (cat, rev) -> CategoryRevenue(category = cat, revenue = round2(rev)) }
    }

    private fun aggregateTopProducts(sales: List<RawSale>): List<TopProduct> {
        if (sales.isEmpty()) return emptyList()
        data class Agg(var units: Int = 0, var revenue: Double = 0.0)

        val map = hashMapOf<String, Agg>()
        sales.forEach { s ->
            val a = map.getOrPut(s.productName) { Agg() }
            a.units += max(0, s.quantity)
            a.revenue += s.total
        }

        return map.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Agg>> { it.value.units }
                    .thenByDescending { it.value.revenue }
            )
            .take(5)
            .map { (name, agg) ->
                TopProduct(
                    name = name,
                    units = agg.units,
                    revenue = round2(agg.revenue)
                )
            }
    }

    private fun round2(v: Double): Double = kotlin.math.round(v * 100.0) / 100.0

    override fun onCleared() {
        super.onCleared()
        listener?.remove()
        listener = null
        ioScope.cancel()
    }
}

/** Internal raw sale row used to compute aggregates */
private data class RawSale(
    val id: String,
    val productName: String,
    val category: String,
    val unitPrice: Double,
    val quantity: Int,
    val total: Double,
    val timestamp: Long,
    val buyerName: String
)
