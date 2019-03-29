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
    private val blackListedPackages = setOf("java.", "javax.", "org.bouncycastle.", "net.i2p.crypto.")

    // Type required for traversal.
    private data class FieldWithObject(val obj: Any, val field: Field)

    // List containing all discovered state pointers.
    private val statePointers = mutableSetOf<StatePointer<*>>()

    // Record seen objects to avoid getting stuck in loops.
    private val seenObjects = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>()).apply { add(state) }

    // Queue of fields to search.
    private val fieldQueue = ArrayDeque<FieldWithObject>().apply { addAllFields(state) }

    // Get fields of class and all super-classes.
    private fun getAllFields(clazz: Class<*>): List<Field> {
        val fields = mutableListOf<Field>()
        var currentClazz = clazz
        while (currentClazz.superclass != null) {
            fields.addAll(currentClazz.declaredFields)
            currentClazz = currentClazz.superclass
        }
        return fields
    }

    // Helper for adding all fields to the queue.
    private fun ArrayDeque<FieldWithObject>.addAllFields(obj: Any) {
        val fields = getAllFields(obj::class.java)

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
                val packageName = obj.javaClass.packageNameOrNull?:""
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


    //interesting - this seems to do a graph search of EVERY FIELD IN EVERY STATE in the TX
    //is this really what should be done? This means that it is impossible to *NOT* attach a statepointer, even if you do not want to
    //maybe TXbuilder should take a param which disables this behaviour.
    fun search(): Set<StatePointer<*>> {
        while (fieldQueue.isNotEmpty()) {
            val (obj, field) = fieldQueue.pop()
            field.isAccessible = true
            handleField(obj, field)
        }
        return statePointers
    }
}

val Class<*>.packageNameOrNull: String? // This intentionally does not go via `package` as that code path is slow and contended and just ends up doing this.
    get() {
        val name = this.getName()
        val i = name.lastIndexOf('.')
        if (i != -1) {
            return name.substring(0, i)
        } else {
            return null
        }
    }