package com.example.assignment.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.example.assignment.data.AdminPurchasingHandle
import com.example.assignment.data.Order
import com.example.assignment.data.SalesAgentHandle

class AdminPurchasingViewModel(
    private val savedStateHandle: SavedStateHandle,
    val handle: AdminPurchasingHandle,
    val salesAgentHandle: SalesAgentHandle
) : ViewModel() {

    // ---- Keys for SavedStateHandle ----
    private companion object {
        const val KEY_ERROR = "ap_error"
        const val KEY_LOADING = "ap_loading"
        const val KEY_SUCCESS = "ap_success"
        const val KEY_DIALOG = "ap_showDialog"
        const val KEY_ORDER_COMPOSITE = "ap_order_composite" // composite key (category+size)
        const val KEY_QTY = "ap_qty"
        const val KEY_STEP = "ap_step"
    }

    // ---- UI state ----
    val availableItems = mutableStateOf<List<Order>>(emptyList())
    val errorMessage = mutableStateOf<String?>(null)
    val isLoading = mutableStateOf(false)
    val successMessage = mutableStateOf<String?>(null)

    val showPurchaseDialog = mutableStateOf(false)
    val purchaseOrder = mutableStateOf<Order?>(null)
    val purchaseQuantity = mutableStateOf("")
    val purchaseStep = mutableStateOf(1)

    init {
        // restore simple primitives
        isLoading.value = savedStateHandle.get<Boolean>(KEY_LOADING) ?: false
        errorMessage.value = savedStateHandle.get<String?>(KEY_ERROR)
        successMessage.value = savedStateHandle.get<String?>(KEY_SUCCESS)
        showPurchaseDialog.value = savedStateHandle.get<Boolean>(KEY_DIALOG) ?: false
        purchaseQuantity.value = savedStateHandle.get<String>(KEY_QTY) ?: ""
        purchaseStep.value = savedStateHandle.get<Int>(KEY_STEP) ?: 1

        // selected order will be re-mapped after fetch
        fetchItems(ensureOrderMapping = true)
    }

    // ---- SavedState: helpers ----
    private fun setLoading(v: Boolean) {
        isLoading.value = v
        savedStateHandle[KEY_LOADING] = v
    }

    private fun setError(msg: String?) {
        errorMessage.value = msg
        savedStateHandle[KEY_ERROR] = msg
    }

    private fun setSuccess(msg: String?) {
        successMessage.value = msg
        savedStateHandle[KEY_SUCCESS] = msg
    }

    private fun setDialog(visible: Boolean) {
        showPurchaseDialog.value = visible
        savedStateHandle[KEY_DIALOG] = visible
    }

    private fun setQuantity(q: String) {
        purchaseQuantity.value = q
        savedStateHandle[KEY_QTY] = q
    }

    private fun setStep(step: Int) {
        purchaseStep.value = step
        savedStateHandle[KEY_STEP] = step
    }

    private fun setOrder(order: Order?) {
        purchaseOrder.value = order
        savedStateHandle[KEY_ORDER_COMPOSITE] = order?.let { orderKeyOf(it) }
    }

    // Stable composite key you already used
    private fun orderKeyOf(o: Order): String = "${o.category}::${o.size}"

    // ---- Public API ----

    fun fetchItems(ensureOrderMapping: Boolean = false) {
        setLoading(true)
        handle.fetchAllOrders { orders, error ->
            setLoading(false)
            if (error != null) {
                setError(error)
            } else {
                val list = orders ?: emptyList()
                availableItems.value = list
                setError(null)

                if (ensureOrderMapping) {
                    val restoredKey = savedStateHandle.get<String>(KEY_ORDER_COMPOSITE)
                    if (restoredKey != null && purchaseOrder.value == null) {
                        list.firstOrNull { orderKeyOf(it) == restoredKey }?.let { setOrder(it) }
                    }
                }
            }
        }
    }

    fun onBuyClicked(order: Order) {
        setOrder(order)
        setQuantity("")
        setStep(1)
        setDialog(true)
    }

    fun confirmPurchase(adminId: String, quantity: Int, onFinish: () -> Unit) {
        val order = purchaseOrder.value ?: return
        setLoading(true)
        handle.purchaseOrder(order, adminId, quantity, salesAgentHandle) { success, error ->
            setLoading(false)
            setDialog(false)
            if (success) {
                setSuccess("Successfully purchased $quantity x ${order.category} (${order.size})")
                setError(null)
                fetchItems()
            } else {
                setError(error ?: "Purchase failed")
                setSuccess(null)
            }
            onFinish()
        }
    }

    /**
     * Called by UI when admin chooses "Remove Product" in the actions dialog.
     * - If your data layer already supports deletion, uncomment the marked block.
     * - Otherwise, the temporary fallback updates UI locally so the app compiles and runs.
     */
    fun removeOrder(order: Order, onDone: (() -> Unit)? = null) {
        setLoading(true)

        // (Optional) optimistic UI: hide the card immediately
        val key = "${order.category}::${order.size}"
        val previous = availableItems.value
        availableItems.value = previous.filterNot { "${it.category}::${it.size}" == key }

        handle.removeOrder(order) { success, error ->
            setLoading(false)
            if (success) {
                setSuccess("Removed ${order.productName}")
                setError(null)
                // Re-sync from Firestore to ensure UI matches backend
                fetchItems()
            } else {
                setError(error ?: "Failed to remove ${order.productName}")
                setSuccess(null)
                // Roll back optimistic change if desired
                availableItems.value = previous
            }
            onDone?.invoke()
        }
    }
}
