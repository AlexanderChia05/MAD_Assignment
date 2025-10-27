package com.example.assignment.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.example.assignment.data.Purchase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.WriteBatch

class AdminBatchingViewModel(
    private val saved: SavedStateHandle
) : ViewModel() {

    // ----------------------------
    // SavedState keys
    // ----------------------------
    private companion object {
        const val K_SEARCH_QUERY = "batch_searchQuery"
        const val K_SEARCH_FIELD = "batch_searchField"
        const val K_SCANNED_CODE = "batch_scannedCode"

        const val K_SHOW_SEARCH = "batch_showSearchDialog"
        const val K_SHOW_CAMERA = "batch_showCameraDialog"
        const val K_SHOW_ACTION = "batch_showProductActionDialog"
        const val K_SHOW_REMOVE = "batch_showRemoveDialog"
        const val K_SHOW_UPDATE = "batch_showUpdateDialog"

        const val K_LAST_CATEGORY = "batch_lastCategoryFilter"
        const val K_SELECTED_PRODUCT_NAME = "batch_selectedProductName"

        const val K_REMOVE_QTY = "batch_removeQuantity"
        const val K_REMOVE_REASON = "batch_removeReason"

        const val K_UPD_NAME = "batch_updateProductName"
        const val K_UPD_SERIAL = "batch_updateSerialNumber"
        const val K_UPD_CATEGORY = "batch_updateCategory"
        const val K_UPD_SIZE = "batch_updateSize"

        const val K_SHOW_SELL = "batch_showSellDialog"
        const val K_SELL_PRODUCT_ID = "batch_sellProductId"
        const val K_SELL_UNIT = "batch_sellUnitText"
        const val K_SELL_QTY = "batch_sellQtyText"
    }

    // ----------------------------
    // Observable UI state
    // ----------------------------
    private val _purchasedItems = mutableStateOf<List<Purchase>>(emptyList()) // AGGREGATED (one card per productName)
    val purchasedItems: State<List<Purchase>> = _purchasedItems

    private val _filteredItems = mutableStateOf<List<Purchase>>(emptyList())
    val filteredItems: State<List<Purchase>> = _filteredItems

    private val _errorMessage = mutableStateOf<String?>(null)
    val errorMessage: State<String?> = _errorMessage

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    private val _showSearchDialog = mutableStateOf(saved.get<Boolean>(K_SHOW_SEARCH) ?: false)
    val showSearchDialog: State<Boolean> = _showSearchDialog

    private val _showCameraDialog = mutableStateOf(saved.get<Boolean>(K_SHOW_CAMERA) ?: false)
    val showCameraDialog: State<Boolean> = _showCameraDialog

    private val _showProductActionDialog = mutableStateOf(saved.get<Boolean>(K_SHOW_ACTION) ?: false)
    val showProductActionDialog: State<Boolean> = _showProductActionDialog

    private val _showRemoveDialog = mutableStateOf(saved.get<Boolean>(K_SHOW_REMOVE) ?: false)
    val showRemoveDialog: State<Boolean> = _showRemoveDialog

    private val _showUpdateDialog = mutableStateOf(saved.get<Boolean>(K_SHOW_UPDATE) ?: false)
    val showUpdateDialog: State<Boolean> = _showUpdateDialog

    private val _searchQuery = mutableStateOf(saved.get<String>(K_SEARCH_QUERY) ?: "")
    val searchQuery: State<String> = _searchQuery

    private val _searchField = mutableStateOf(saved.get<String>(K_SEARCH_FIELD) ?: "Name") // "Name" | "S.Num" | "Size" | "Cate."
    val searchField: State<String> = _searchField

    private val _scannedCode = mutableStateOf(saved.get<String>(K_SCANNED_CODE) ?: "")
    val scannedCode: State<String> = _scannedCode

    private val _selectedPurchase = mutableStateOf<Purchase?>(null) // aggregated card
    val selectedPurchase: State<Purchase?> = _selectedPurchase

    // Sell dialog persisted state
    private val _showSellDialog = mutableStateOf(saved.get<Boolean>(K_SHOW_SELL) ?: false)
    val showSellDialog: State<Boolean> = _showSellDialog

    private val _sellProductId = mutableStateOf(saved.get<String?>(K_SELL_PRODUCT_ID))
    val sellProductId: State<String?> = _sellProductId

    private val _sellUnitText = mutableStateOf(saved.get<String>(K_SELL_UNIT) ?: "")
    val sellUnitText: State<String> = _sellUnitText

    private val _sellQtyText = mutableStateOf(saved.get<String>(K_SELL_QTY) ?: "")
    val sellQtyText: State<String> = _sellQtyText

    // Remove dialog inputs
    private val _removeQuantity = mutableStateOf(saved.get<String>(K_REMOVE_QTY) ?: "")
    val removeQuantity: State<String> = _removeQuantity

    private val _removeReason = mutableStateOf(saved.get<String>(K_REMOVE_REASON) ?: "")
    val removeReason: State<String> = _removeReason

    // Update dialog inputs
    private val _updateProductName = mutableStateOf(saved.get<String>(K_UPD_NAME) ?: "")
    val updateProductName: State<String> = _updateProductName

    private val _updateSerialNumber = mutableStateOf(saved.get<String>(K_UPD_SERIAL) ?: "")
    val updateSerialNumber: State<String> = _updateSerialNumber

    private val _updateCategory = mutableStateOf(saved.get<String>(K_UPD_CATEGORY) ?: "")
    val updateCategory: State<String> = _updateCategory

    private val _updateSize = mutableStateOf(saved.get<String>(K_UPD_SIZE) ?: "")
    val updateSize: State<String> = _updateSize

    // ----------------------------
    // Firestore + fetch memory
    // ----------------------------
    private val db by lazy { FirebaseFirestore.getInstance() }
    private var lastCategoryFilter: String? = saved.get<String>(K_LAST_CATEGORY)

    // Internal: productName -> list of underlying docs (newest first)
    private val productToDocs = linkedMapOf<String, List<PurchaseDocMeta>>()

    private data class PurchaseDocMeta(
        val docId: String,
        val productName: String,
        val serialNumber: String?,
        val category: String,
        val size: String,
        val quantity: Int,
        val price: Double,
        val totalPrice: Double,
        val purchaseDate: Long,
        val orderId: String,
        val isAdminPurchase: Boolean
    )

    init {
        // On creation (or restoration), re-fetch using the last category if any.
        // Filters/search state are already restored and will be applied after fetch.
        fetchPurchasedItems(lastCategoryFilter)
    }

    // ---------------------------------------
    // Load purchases (combine by productName)
    // ---------------------------------------
    fun fetchPurchasedItems(category: String? = null) {
        lastCategoryFilter = category
        saved[K_LAST_CATEGORY] = category

        _isLoading.value = true
        _errorMessage.value = null

        // Avoid composite-index error:
        // - If category filter, skip server orderBy and sort client-side.
        // - Else, order on server.
        var q: Query = db.collection("purchases")
        val sortClientSide: Boolean
        if (!category.isNullOrBlank()) {
            q = q.whereEqualTo("category", category)
            sortClientSide = true
        } else {
            q = q.orderBy("purchaseDate", Query.Direction.DESCENDING)
            sortClientSide = false
        }

        q.get()
            .addOnSuccessListener { snap ->
                val docs = snap.documents.mapNotNull { d ->
                    try {
                        PurchaseDocMeta(
                            docId = d.id,
                            productName = d.getString("productName") ?: return@mapNotNull null,
                            serialNumber = d.getString("serialNumber"),
                            category = d.getString("category") ?: "",
                            size = d.getString("size") ?: "",
                            quantity = (d.getLong("quantity") ?: 0L).toInt(),
                            price = d.getDouble("price") ?: (d.getLong("price")?.toDouble() ?: 0.0),
                            totalPrice = d.getDouble("totalPrice")
                                ?: (d.getLong("totalPrice")?.toDouble()
                                    ?: ((d.getDouble("price") ?: 0.0) * (d.getLong("quantity") ?: 0L).toDouble())),
                            purchaseDate = d.getLong("purchaseDate") ?: 0L,
                            orderId = d.getString("orderId") ?: "",
                            isAdminPurchase = d.getBoolean("isAdminPurchase") ?: false
                        )
                    } catch (_: Throwable) { null }
                }

                val base = if (sortClientSide) docs.sortedByDescending { it.purchaseDate } else docs

                // Only show in-stock docs
                val inStock = base.filter { it.quantity > 0 }

                // Group by productName; newest-first in each group
                val groups = inStock.groupBy { it.productName }.mapValues { (_, list) ->
                    list.sortedByDescending { it.purchaseDate }
                }

                // Save internal index
                productToDocs.clear()
                productToDocs.putAll(groups)

                // Build aggregated UI cards (one per productName)
                val aggregated = groups.map { (name, list) ->
                    val totalQty = list.sumOf { it.quantity }
                    val totalCost = list.sumOf { it.totalPrice }
                    val head = list.first() // newest lot representative
                    Purchase(
                        id = head.docId,                 // representative (UI key)
                        orderId = head.orderId,
                        productName = name,              // aggregation key
                        serialNumber = head.serialNumber,
                        category = head.category,
                        size = head.size,
                        quantity = totalQty,             // aggregated qty
                        price = head.price,              // show last lot price
                        totalPrice = totalCost,          // aggregated total cost
                        purchaseDate = head.purchaseDate,// newest date
                        isAdminPurchase = head.isAdminPurchase
                    )
                }.sortedByDescending { it.purchaseDate }

                _purchasedItems.value = aggregated
                // Try to restore previously selected product, if still present
                restoreSelectedIfPossible()
                applyFilters()
            }
            .addOnFailureListener { e -> _errorMessage.value = e.message }
            .addOnCompleteListener { _isLoading.value = false }
    }

    private fun restoreSelectedIfPossible() {
        val wantedName = saved.get<String>(K_SELECTED_PRODUCT_NAME) ?: return
        val found = _purchasedItems.value.firstOrNull { it.productName == wantedName }
        if (found != null) {
            _selectedPurchase.value = found
            // also refresh the inline update fields (they’re persisted too, but this keeps them in sync)
            _updateProductName.value = saved.get<String>(K_UPD_NAME) ?: found.productName
            _updateSerialNumber.value = saved.get<String>(K_UPD_SERIAL) ?: (found.serialNumber ?: "")
            _updateCategory.value = saved.get<String>(K_UPD_CATEGORY) ?: found.category
            _updateSize.value = saved.get<String>(K_UPD_SIZE) ?: found.size
        } else {
            _selectedPurchase.value = null
        }
    }

    // --------------------
    // Search / filter
    // --------------------
    fun onSearchClicked() {
        _showSearchDialog.value = true
        saved[K_SHOW_SEARCH] = true
    }
    fun onDismissSearch() {
        _showSearchDialog.value = false
        saved[K_SHOW_SEARCH] = false
    }

    fun updateSearchQuery(q: String) {
        _searchQuery.value = q
        saved[K_SEARCH_QUERY] = q
        applyFilters()
    }

    fun updateSearchField(field: String) {
        _searchField.value = field
        saved[K_SEARCH_FIELD] = field
        applyFilters()
    }

    fun performSearch() = applyFilters()

    fun clearFilters() {
        _searchQuery.value = ""
        _scannedCode.value = ""
        saved[K_SEARCH_QUERY] = ""
        saved[K_SCANNED_CODE] = ""
        _filteredItems.value = _purchasedItems.value
    }

    private fun applyFilters() {
        val base = _purchasedItems.value
        val q = _searchQuery.value.trim()
        val hasScan = _scannedCode.value.isNotBlank()

        val filtered = base.filter { p ->
            val searchOk = if (q.isBlank()) true else when (_searchField.value) {
                "Name"  -> p.productName.contains(q, ignoreCase = true)
                "S.Num" -> (p.serialNumber ?: "").contains(q, ignoreCase = true)
                "Size"  -> p.size.contains(q, ignoreCase = true)
                "Cate." -> p.category.contains(q, ignoreCase = true)
                else    -> true
            }
            val scanOk = if (!hasScan) true else {
                val code = _scannedCode.value
                (p.serialNumber ?: "").equals(code, ignoreCase = true) ||
                        p.productName.equals(code, ignoreCase = true) ||
                        p.orderId.equals(code, ignoreCase = true)
            }
            searchOk && scanOk
        }
        _filteredItems.value = filtered
    }

    // ---------------
    // Camera actions
    // ---------------
    fun onCameraClicked() {
        _showCameraDialog.value = true
        saved[K_SHOW_CAMERA] = true
    }
    fun onDismissCamera() {
        _showCameraDialog.value = false
        saved[K_SHOW_CAMERA] = false
    }

    fun processBarcodeResult(raw: String) {
        _scannedCode.value = raw
        saved[K_SCANNED_CODE] = raw
        _showCameraDialog.value = false
        saved[K_SHOW_CAMERA] = false
        applyFilters()
    }

    // ------------------
    // Item click / menus
    // ------------------
    fun onItemClicked(p: Purchase) {
        _selectedPurchase.value = p // aggregated card
        saved[K_SELECTED_PRODUCT_NAME] = p.productName

        _updateProductName.value = p.productName
        _updateSerialNumber.value = p.serialNumber ?: ""
        _updateCategory.value = p.category
        _updateSize.value = p.size

        saved[K_UPD_NAME] = _updateProductName.value
        saved[K_UPD_SERIAL] = _updateSerialNumber.value
        saved[K_UPD_CATEGORY] = _updateCategory.value
        saved[K_UPD_SIZE] = _updateSize.value

        _showProductActionDialog.value = true
        saved[K_SHOW_ACTION] = true
    }

    fun onDismissProductAction() {
        _showProductActionDialog.value = false
        saved[K_SHOW_ACTION] = false
    }

    // ---- Remove ----
    fun onRemoveProductClicked() {
        _showRemoveDialog.value = true
        saved[K_SHOW_REMOVE] = true
    }
    fun onDismissRemove() {
        _showRemoveDialog.value = false
        saved[K_SHOW_REMOVE] = false
    }

    fun updateRemoveQuantity(q: String) {
        _removeQuantity.value = q
        saved[K_REMOVE_QTY] = q
    }
    fun updateRemoveReason(r: String) {
        _removeReason.value = r
        saved[K_REMOVE_REASON] = r
    }

    /**
     * Remove [toRemove] units from the aggregated product (newest lots first).
     * Decrements across multiple docs in a transaction and REFRESHES list after success.
     */
    fun removeProduct(done: (Boolean, String?) -> Unit) {
        val card = _selectedPurchase.value ?: return done(false, "No product selected")
        val toRemove = _removeQuantity.value.toIntOrNull() ?: return done(false, "Enter a valid quantity")
        if (toRemove <= 0) return done(false, "Quantity must be > 0")

        val docs = productToDocs[card.productName].orEmpty()
        if (docs.isEmpty()) return done(false, "No stock for ${card.productName}")

        db.runTransaction { tx ->
            var remaining = toRemove
            for (meta in docs) {
                if (remaining <= 0) break
                val ref = db.collection("purchases").document(meta.docId)
                val snap = tx.get(ref)
                if (!snap.exists()) continue
                val curQty = (snap.getLong("quantity") ?: 0L).toInt()
                if (curQty <= 0) continue

                val deduct = minOf(remaining, curQty)
                val newQty = curQty - deduct
                if (newQty > 0) tx.update(ref, mapOf("quantity" to newQty)) else tx.delete(ref)
                remaining -= deduct
            }
            if (remaining > 0) throw IllegalArgumentException("Only ${toRemove - remaining} available to remove")
            null
        }.addOnSuccessListener {
            _showRemoveDialog.value = false
            saved[K_SHOW_REMOVE] = false
            done(true, "Removed $toRemove from ${card.productName}")
            // Refresh so UI shows updated quantity
            fetchPurchasedItems(lastCategoryFilter)
        }.addOnFailureListener { e ->
            done(false, e.message)
        }
    }

    // ---- Update Info (apply to ALL docs for this product) ----
    fun onUpdateInfoClicked() {
        _showUpdateDialog.value = true
        saved[K_SHOW_UPDATE] = true
    }
    fun onDismissUpdate() {
        _showUpdateDialog.value = false
        saved[K_SHOW_UPDATE] = false
    }

    fun updateProductName(v: String) {
        _updateProductName.value = v
        saved[K_UPD_NAME] = v
    }
    fun updateSerialNumber(v: String) {
        _updateSerialNumber.value = v
        saved[K_UPD_SERIAL] = v
    }
    fun updateCategory(v: String) {
        _updateCategory.value = v
        saved[K_UPD_CATEGORY] = v
    }
    fun updateSize(v: String) {
        _updateSize.value = v
        saved[K_UPD_SIZE] = v
    }

    fun updateProductInfo(done: (Boolean, String?) -> Unit) {
        val card = _selectedPurchase.value ?: return done(false, "No product selected")
        val docs = productToDocs[card.productName].orEmpty()
        if (docs.isEmpty()) return done(false, "No docs to update")

        val updates = hashMapOf<String, Any>(
            "productName" to _updateProductName.value.trim(),
            "category" to _updateCategory.value.trim(),
            "size" to _updateSize.value.trim(),
        )
        updates["serialNumber"] = _updateSerialNumber.value.trim()

        val batch: WriteBatch = db.batch()
        docs.forEach { meta ->
            val ref = db.collection("purchases").document(meta.docId)
            @Suppress("UNCHECKED_CAST")
            batch.update(ref, updates as Map<String, Any>)
        }
        batch.commit()
            .addOnSuccessListener {
                _showUpdateDialog.value = false
                saved[K_SHOW_UPDATE] = false
                done(true, "Product info updated")
                // Refresh to reflect updated fields
                fetchPurchasedItems(lastCategoryFilter)
            }
            .addOnFailureListener { e -> done(false, e.message) }
    }

    // ---- SELL dialog setters (public) ----
    fun setShowSellDialog(v: Boolean) { _showSellDialog.value = v; saved[K_SHOW_SELL] = v }
    fun setSellProductId(id: String?) { _sellProductId.value = id; saved[K_SELL_PRODUCT_ID] = id }
    fun setSellUnitText(text: String) { _sellUnitText.value = text; saved[K_SELL_UNIT] = text }
    fun setSellQtyText(text: String) { _sellQtyText.value = text; saved[K_SELL_QTY] = text }


    // ---------------
    // SELL (aggregate-aware)
    // ---------------
    /**
     * Sell [sellQty] units from the aggregated product (newest lots first).
     * Atomically:
     *  1) Decrement (or delete) one or more "purchases" docs.
     *  2) Write ONE sale in "sales" (dashboard listens to this).
     * Then REFRESH the list so the card updates.
     */
    fun sellProduct(
        purchase: Purchase,       // aggregated card
        sellQty: Int,
        sellUnitPrice: Double,
        buyerName: String,
        done: (Boolean, String?) -> Unit
    ) {
        if (sellQty <= 0) return done(false, "Quantity must be > 0")

        val docs = productToDocs[purchase.productName].orEmpty()
        if (docs.isEmpty()) return done(false, "No stock for ${purchase.productName}")

        db.runTransaction { tx ->
            var remaining = sellQty
            var totalDeducted = 0

            // Consume from newest lots first
            for (meta in docs) {
                if (remaining <= 0) break
                val ref = db.collection("purchases").document(meta.docId)
                val snap = tx.get(ref)
                if (!snap.exists()) continue
                val curQty = (snap.getLong("quantity") ?: 0L).toInt()
                if (curQty <= 0) continue

                val deduct = minOf(remaining, curQty)
                val newQty = curQty - deduct
                if (newQty > 0) tx.update(ref, mapOf("quantity" to newQty)) else tx.delete(ref)
                remaining -= deduct
                totalDeducted += deduct
            }

            if (remaining > 0) throw IllegalArgumentException("Only $totalDeducted available to sell")

            // Write ONE sale doc summarizing this sale action
            val salesRef = db.collection("sales").document()
            val sale = hashMapOf(
                "productName" to purchase.productName,
                "category" to purchase.category,
                "unitPrice" to sellUnitPrice,
                "quantity" to sellQty,
                "timestamp" to System.currentTimeMillis(),
                "buyerName" to buyerName,
                "total" to sellUnitPrice * sellQty
            )
            tx.set(salesRef, sale)

            null
        }.addOnSuccessListener {
            done(true, null)
            // Refresh so UI shows updated quantity
            fetchPurchasedItems(lastCategoryFilter)
        }.addOnFailureListener { e ->
            done(false, e.message)
        }
    }
}
