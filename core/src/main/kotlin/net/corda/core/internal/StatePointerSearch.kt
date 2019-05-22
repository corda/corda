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
    // Classes in these packages should not be part of a search.
    private val blackListedPackages = setOf("java.", "javax.", "org.bouncycastle.", "net.i2p.crypto.")

    // Type required for traversal.
    private data class FieldWithObject(val obj: Any, val field: Field)

    // List containing all discovered state pointers.
    private val statePointers = mutableSetOf<StatePointer<*>>()

    // Record seen objects to avoid getting stuck in loops.
    private val seenObjects = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>()).apply { add(state) }

    // Queue of fields to search.
    private val fieldQueue = ArrayDeque<FieldWithObject>().apply { addAllFields(state) }

    // Helper for adding all fields to the queue.
    private fun ArrayDeque<FieldWithObject>.addAllFields(obj: Any) {
        val fields = FieldUtils.getAllFieldsList(obj::class.java)

        val fieldsWithObjects = fields.mapNotNull { field ->
            // Ignore classes which have not been loaded.
            // Assumption: all required state classes are already loaded.
            val packageName = field.type.packageNameOrNull
            if (packageName == null) {
                null
            } else {
                FieldWithObject(obj, field)
            }
        }
        addAll(fieldsWithObjects)
    }

    private fun handleIterable(iterable: Iterable<*>) {
        iterable.forEach { obj -> handleObject(obj) }
    }

    private fun handleMap(map: Map<*, *>) {
        map.forEach { k, v ->
            handleObject(k)
            handleObject(v)
        }
    }

    private fun handleObject(obj: Any?) {
        if (obj == null) return
        seenObjects.add(obj)
        when (obj) {
            is Map<*, *> -> handleMap(obj)
            is StatePointer<*> -> statePointers.add(obj)
            is Iterable<*> -> handleIterable(obj)
            else -> {
                val packageName = obj.javaClass.packageNameOrNull ?: ""
                val isBlackListed = blackListedPackages.any { packageName.startsWith(it) }
                if (isBlackListed.not()) fieldQueue.addAllFields(obj)
            }
        }
    }

    private fun handleField(obj: Any, field: Field) {
        val newObj = field.get(obj) ?: return
        if (newObj in seenObjects) return
        handleObject(newObj)
    }

    fun search(): Set<StatePointer<*>> {
        while (fieldQueue.isNotEmpty()) {
            val (obj, field) = fieldQueue.pop()
            field.isAccessible = true
            handleField(obj, field)
        }
        return statePointers
    }
}