package com.example.assignment.viewmodel

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Safe helpers for SavedStateHandle <-> StateFlow without putting non-parcelables
 * into the SavedStateHandle bundle.
 *
 * Usage (unchanged):
 *   val name = saved.getOrCreateStateFlow("key_name", "")
 *   fun setName(v: String) = saved.updateKey("key_name", v)
 *
 * DO NOT call .value = on the StateFlow directly; always use updateKey().
 */
object KeyedStateSupport {

    // Global cache so we don’t stash flows inside SavedStateHandle.
    // Key format: "${identityHashCode(handle)}::$key"
    private val flowCache = ConcurrentHashMap<String, MutableStateFlow<Any?>>()

    @Suppress("UNCHECKED_CAST")
    fun <T> SavedStateHandle.getOrCreateStateFlow(
        key: String,
        default: T
    ): MutableStateFlow<T> {
        val cacheKey = "${System.identityHashCode(this)}::$key"
        flowCache[cacheKey]?.let { return it as MutableStateFlow<T> }

        val initial = get<T>(key) ?: default
        val flow = MutableStateFlow(initial)
        flowCache[cacheKey] = flow as MutableStateFlow<Any?>
        return flow
    }

    /**
     * Write-through update: writes to SavedStateHandle and (if present) the cached StateFlow.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> SavedStateHandle.updateKey(
        key: String,
        value: T
    ) {
        set(key, value)
        val cacheKey = "${System.identityHashCode(this)}::$key"
        (flowCache[cacheKey] as? MutableStateFlow<T>)?.update { value }
    }

    /**
     * Optional: mirror any source StateFlow into SavedStateHandle with a given scope.
     * (Does not store the flow inside the handle.)
     */
    fun <T> SavedStateHandle.mirrorInto(
        scope: CoroutineScope,
        key: String,
        source: StateFlow<T>
    ) {
        // seed if handle has a different existing value
        val existing = get<T>(key)
        if (existing == null || existing != source.value) {
            set(key, source.value)
            // also nudge cache if created
            val cacheKey = "${System.identityHashCode(this)}::$key"
            @Suppress("UNCHECKED_CAST")
            (flowCache[cacheKey] as? MutableStateFlow<T>)?.update { source.value }
        }
        scope.launch {
            source.collect { v ->
                set(key, v)
                val cacheKey = "${System.identityHashCode(this@mirrorInto)}::$key"
                @Suppress("UNCHECKED_CAST")
                (flowCache[cacheKey] as? MutableStateFlow<T>)?.update { v }
            }
        }
    }
}
