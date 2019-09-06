package net.corda.core.internal

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StatePointer
import org.apache.commons.lang3.reflect.FieldUtils
import java.lang.reflect.Field
import java.util.*

/**
 * Uses reflection to search for instances of [StatePointer] within a [ContractState].
 * TODO: Doesn't handle calculated properties. Add support for this.
 */
class StatePointerSearch(val state: ContractState) {
    private companion object {
        // Classes in these packages should not be part of a search.
        private val blackListedPackages = setOf("java.", "javax.", "org.bouncycastle.", "net.i2p.crypto.")
    }

    // Type required for traversal.
    private data class FieldWithObject(val obj: Any, val field: Field)

    // List containing all discovered state pointers.
    private val statePointers = mutableSetOf<StatePointer<*>>()

    // Record seen objects to avoid getting stuck in loops.
    private val seenObjects = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())

    // Queue of fields to search.
    private val fieldQueue = ArrayDeque<FieldWithObject>()

    // Helper for adding all fields to the queue.
    private fun addAllFields(obj: Any) {
        val fields = FieldUtils.getAllFieldsList(obj::class.java)

        fields.mapNotNullTo(fieldQueue) { field ->
            if (field.isSynthetic || field.isStatic) return@mapNotNullTo null
            // Ignore classes which have not been loaded.
            // Assumption: all required state classes are already loaded.
            val packageName = field.type.packageNameOrNull
            if (packageName == null) {
                null
            } else {
                FieldWithObject(obj, field)
            }
        }
    }

    private fun handleIterable(iterable: Iterable<*>) {
        iterable.forEach(::handleObject)
    }

    private fun handleMap(map: Map<*, *>) {
        map.forEach { k, v ->
            handleObject(k)
            handleObject(v)
        }
    }

    private fun handleObject(obj: Any?) {
        if (obj == null || !seenObjects.add(obj)) return
        when (obj) {
            is Map<*, *> -> handleMap(obj)
            is StatePointer<*> -> statePointers.add(obj)
            is Iterable<*> -> handleIterable(obj)
            else -> {
                val packageName = obj.javaClass.packageNameOrNull ?: ""
                val isBlackListed = blackListedPackages.any { packageName.startsWith(it) }
                if (!isBlackListed) addAllFields(obj)
            }
        }
    }

    fun search(): Set<StatePointer<*>> {
        handleObject(state)
        while (fieldQueue.isNotEmpty()) {
            val (obj, field) = fieldQueue.pop()
            field.isAccessible = true
            handleObject(field.get(obj))
        }
        return statePointers
    }
}