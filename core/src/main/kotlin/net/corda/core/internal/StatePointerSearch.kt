package net.corda.core.internal

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StatePointer
import java.lang.reflect.Field
import java.util.*

/**
 * Uses reflection to search for instances of [StatePointer] within a [ContractState].
 * TODO: Doesn't handle calculated properties. Add support for this.
 */
class StatePointerSearch(val state: ContractState) {
    // Classes in these packages should not be part of a search.
    private val blackListedPackages = setOf("java.", "javax.", "org.bouncycastle", "org.hibernate")

    // Type required for traversal.
    private data class FieldWithObject(val obj: Any, val field: Field)

    // List containing all discovered state pointers.
    private val statePointers = mutableSetOf<StatePointer<*>>()

    // Record seen objects to avoid getting stuck in loops.
    private val seenObjects = mutableSetOf<Any>().apply { add(state) }

    // Queue of fields to search.
    private val fieldQueue = ArrayDeque<FieldWithObject>().apply { addAllFields(state) }

    // Helper for adding all fields to the queue.
    private fun ArrayDeque<FieldWithObject>.addAllFields(obj: Any) {
        val fields = obj::class.java.declaredFields
        val fieldsWithObjects = fields.mapNotNull { field ->
            // Ignore classes which have not been loaded.
            // Assumption: all required state classes are already loaded.
            val packageName = field.type.`package`?.name
            if (packageName == null) {
                null
            } else {
                FieldWithObject(obj, field)
            }
        }
        addAll(fieldsWithObjects)
    }

    private fun isStatePointer(obj: Any): Boolean {
        return StatePointer::class.java.isAssignableFrom(obj.javaClass)
    }

    private fun isMap(obj: Any): Boolean {
        return java.util.Map::class.java.isAssignableFrom(obj.javaClass) ||
                kotlin.collections.Map::class.java.isAssignableFrom(obj.javaClass)
    }

    private fun isIterable(obj: Any): Boolean {
        return java.lang.Iterable::class.java.isAssignableFrom(obj.javaClass) ||
                kotlin.collections.Iterable::class.java.isAssignableFrom(obj.javaClass)
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
        when {
            isMap(obj) -> handleMap(obj as Map<*,*>)
            isStatePointer(obj) -> statePointers.add(obj as StatePointer<*>)
            isIterable(obj) -> handleIterable(obj as Iterable<*>)
            else -> {
                val packageName = obj.javaClass.`package`.name
                val isBlackListed = blackListedPackages.any { packageName.startsWith(it) }
                if (isBlackListed.not()) fieldQueue.addAllFields(obj)
            }
        }
    }

    private fun handleField(obj: Any, field: Field) {
        val newObj = field.get(obj) ?: return
        if (newObj in seenObjects) return
        seenObjects.add(obj)
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