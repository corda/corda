package net.corda.nodeapi.internal.serialization.kryo

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.serializers.CompatibleFieldSerializer
import net.corda.core.internal.fullyQualifiedPackage
import org.apache.commons.lang3.reflect.FieldUtils
import java.lang.reflect.Field
import java.util.Collections

class IteratorSerializer(type: Class<*>, kryo: Kryo) : Serializer<Iterator<*>>(false, false) {
    private val serializer = if (type.isPackageOpen) {
        val config = CompatibleFieldSerializer.CompatibleFieldSerializerConfig().apply {
            ignoreSyntheticFields = false
            extendedFieldNames = true
        }
        CompatibleFieldSerializer<Iterator<*>>(kryo, type, config)
    } else {
        null
    }

    private val iterableReferenceField = findField(type, "this\$0")
    private val expectedModCountField = findField(type, "expectedModCount")
    private val modCountField = iterableReferenceField?.type?.let { findField(it, "modCount") }

    override fun write(kryo: Kryo, output: Output, obj: Iterator<*>) {
        if (serializer != null) {
            serializer.write(kryo, output, obj)
        } else {
            val hasNext = obj.hasNext()
            output.writeBoolean(hasNext)
        }
    }

    override fun read(kryo: Kryo, input: Input, type: Class<out Iterator<*>>): Iterator<*> {
        return if (serializer != null) {
            val iterator = serializer.read(kryo, input, type)
            fixIterator(iterator)
        } else {
            val hasNext = input.readBoolean()
            if (hasNext) {
                throw UnsupportedOperationException("Restoring checkpoints containing iterators is not supported in this test environment. " +
                        "If you wish to restore these checkpoints in your tests then use the out-of-process node driver, or add " +
                        "--add-opens=${type.fullyQualifiedPackage}=ALL-UNNAMED to the test JVM args.")
            } else {
                // If the iterator didn't have any elements left (e.g. iterating over a singleton Collection) then there's no need to make a
                // fuss. We can return an empty iterator and move on.
                Collections.emptyIterator<Any>()
            }
        }
    }

    private fun fixIterator(iterator: Iterator<*>): Iterator<*> {
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
        return FieldUtils.getField(clazz, fieldName)?.takeIf { it.trySetAccessible() }
    }
}
