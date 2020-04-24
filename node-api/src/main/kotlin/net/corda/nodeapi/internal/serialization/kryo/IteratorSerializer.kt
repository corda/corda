package net.corda.nodeapi.internal.serialization.kryo

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import java.lang.reflect.Field

class IteratorSerializer(type: Class<*>, private val serializer: Serializer<Iterator<*>>) : Serializer<Iterator<*>>(false, false) {

    private val iterableReferenceField = findField(type, "this\$0")?.apply { isAccessible = true }
    private val expectedModCountField = findField(type, "expectedModCount")?.apply { isAccessible = true }
    private val iterableReferenceFieldType = iterableReferenceField?.type
    private val modCountField = when (iterableReferenceFieldType) {
        null -> null
        else -> findField(iterableReferenceFieldType, "modCount")?.apply { isAccessible = true }
    }

    override fun write(kryo: Kryo, output: Output, obj: Iterator<*>) {
        serializer.write(kryo, output, obj)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<Iterator<*>>): Iterator<*> {
        val iterator = serializer.read(kryo, input, type)
        return fixIterator(iterator)
    }

    private fun fixIterator(iterator: Iterator<*>) : Iterator<*> {

        // Set expectedModCount of iterator
        val iterableInstance = iterableReferenceField?.get(iterator) ?: return iterator
        val modCountValue = modCountField?.getInt(iterableInstance) ?: return iterator
        expectedModCountField?.setInt(iterator, modCountValue)

        return iterator
    }

    /**
     * Find field in clazz or any superclass
     */
    private fun findField(clazz: Class<*>, fieldName: String): Field? {
        return clazz.declaredFields.firstOrNull { x -> x.name == fieldName } ?: when {
            clazz.superclass != null -> {
                // Look in superclasses
                findField(clazz.superclass, fieldName)
            }
            else -> null // Not found
        }
    }
}


