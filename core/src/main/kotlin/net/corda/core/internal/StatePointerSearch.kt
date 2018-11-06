package net.corda.core.internal

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.LinearPointer
import net.corda.core.contracts.StatePointer
import net.corda.core.contracts.StaticPointer
import java.lang.reflect.Field
import java.util.*

/**
 * Uses reflection to search for instances of [StatePointer] within a [ContractState].
 */
class StatePointerSearch(val state: ContractState) {
    // Classes in these packages should not be part of a search.
    private val blackListedPackages = setOf("java.", "javax.")

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
                // Ignore JDK classes.
                val isBlacklistedPackage = blackListedPackages.any { packageName.startsWith(it) }
                if (isBlacklistedPackage) {
                    null
                } else {
                    FieldWithObject(obj, field)
                }
            }
        }
        addAll(fieldsWithObjects)
    }

    private fun handleField(obj: Any, field: Field) {
        when {
            // StatePointer. Handles nullable StatePointers too.
            field.type == LinearPointer::class.java -> statePointers.add(field.get(obj) as? LinearPointer<*> ?: return)
            field.type == StaticPointer::class.java -> statePointers.add(field.get(obj) as? StaticPointer<*> ?: return)
            // Not StatePointer.
            else -> {
                val newObj = field.get(obj) ?: return

                // Ignore nulls.
                if (newObj in seenObjects) {
                    return
                }

                // Recurse.
                fieldQueue.addAllFields(newObj)
                seenObjects.add(obj)
            }
        }
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