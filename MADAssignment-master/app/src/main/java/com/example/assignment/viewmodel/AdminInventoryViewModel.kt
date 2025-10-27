package com.example.assignment.viewmodel

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.assignment.data.Purchase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AdminInventoryViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    // Keys for SavedStateHandle
    private companion object {
        const val KEY_LOADING = "inv_isLoading"
        const val KEY_ERROR = "inv_errorMessage"
        // If later you add filters like category/search, define keys here too:
        // const val KEY_SELECTED_CATEGORY = "inv_selectedCategory"
        // const val KEY_SEARCH_QUERY = "inv_searchQuery"
    }

    // These two are safely persisted (primitives/strings).
    // We seed from SavedStateHandle so they restore after rotation/process recreation.
    val isLoading = mutableStateOf(savedStateHandle.get<Boolean>(KEY_LOADING) ?: false)
    val errorMessage = mutableStateOf<String?>(savedStateHandle.get<String?>(KEY_ERROR))

    // These complex maps are *not* stored in SavedStateHandle (they usually contain custom types
    // that aren't Bundle-parcelable). They will be recomputed by fetchPurchasedItems().
    val categoryRatios = mutableStateOf<Map<String, Float>>(emptyMap())
    val categoriesWithProducts = mutableStateOf<Map<String, List<Purchase>>>(emptyMap())

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    /** Small setters that also mirror into SavedStateHandle */
    private fun setLoading(value: Boolean) {
        isLoading.value = value
        savedStateHandle[KEY_LOADING] = value
    }

    private fun setError(value: String?) {
        errorMessage.value = value
        savedStateHandle[KEY_ERROR] = value
    }

    fun fetchPurchasedItems() {
        viewModelScope.launch {
            setLoading(true)
            try {
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    setError("User not authenticated. Please log in.")
                    Log.e("AdminInventoryViewModel", "User not authenticated")
                    setLoading(false)
                    return@launch
                }

                val snapshot = firestore.collection("purchases")
                    .whereEqualTo("isAdminPurchase", true)
                    .get()
                    .await()

                val items = snapshot.documents.mapNotNull { doc ->
                    try {
                        val purchase = doc.toObject(Purchase::class.java)
                        purchase?.copy(id = doc.id)
                    } catch (e: Exception) {
                        Log.e(
                            "AdminInventoryViewModel",
                            "Failed to parse purchase document ${doc.id}: ${e.message}"
                        )
                        null
                    }
                }

                // Ensure valid categories and quantities
                val groupedByCategory = items.groupBy { it.category.ifEmpty { "Uncategorized" } }
                val ratios = groupedByCategory.mapValues { entry ->
                    entry.value.sumOf { purchase -> purchase.quantity.toLong() }.toFloat()
                }

                categoryRatios.value = ratios
                categoriesWithProducts.value = groupedByCategory
                setError(if (items.isEmpty()) "No inventory items found" else null)

                Log.d(
                    "AdminInventoryViewModel",
                    "Fetched ${items.size} purchases across ${groupedByCategory.size} categories"
                )
            } catch (e: FirebaseFirestoreException) {
                val message = when (e.code) {
                    FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                        "Permission denied: Check Firestore rules"
                    FirebaseFirestoreException.Code.NOT_FOUND ->
                        "No purchases found"
                    FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                        "Operation timed out"
                    else -> "Failed to load inventory: ${e.message}"
                }
                setError(message)
                Log.e(
                    "AdminInventoryViewModel",
                    "Firestore error: ${e.message}, Code: ${e.code}", e
                )
            } catch (e: Exception) {
                setError("Unexpected error: ${e.message}")
                Log.e(
                    "AdminInventoryViewModel",
                    "Unexpected error fetching purchases: ${e.message}", e
                )
            } finally {
                setLoading(false)
            }
        }
    }
}
