package net.corda.nodeapi.internal.serialization.kryo

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.SerializerFactory
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.serializers.CompatibleFieldSerializer
import net.corda.core.internal.fullyQualifiedPackage
import java.util.Collections
import java.util.LinkedList

object IteratorSerializerFactory : SerializerFactory.BaseSerializerFactory<Serializer<*>>() {
    private val linkedListListIteratorClass = LinkedList<Any>().listIterator()::class.java

    override fun newSerializer(kryo: Kryo, type: Class<*>): Serializer<out Iterator<*>> {
        return when {
            !type.isPackageOpen -> FallbackEmptyIteratorSerializer
            type == linkedListListIteratorClass -> LinkedListListIteratorSerializer
            else -> {
                val config = CompatibleFieldSerializer.CompatibleFieldSerializerConfig().apply {
                    ignoreSyntheticFields = false
                    extendedFieldNames = true
                }
                CompatibleFieldSerializer(kryo, type, config)
            }
        }
    }

    private object LinkedListListIteratorSerializer : Serializer<ListIterator<*>>() {
        private val outerListField = linkedListListIteratorClass.getDeclaredField("this$0").apply { isAccessible = true }

        override fun write(kryo: Kryo, output: Output, obj: ListIterator<*>) {
            kryo.writeClassAndObject(output, outerListField.get(obj))
            output.writeInt(obj.nextIndex())
        }

        override fun read(kryo: Kryo, input: Input, type: Class<out ListIterator<*>>): ListIterator<*> {
            val list = kryo.readClassAndObject(input) as LinkedList<*>
            val index = input.readInt()
            return list.listIterator(index)
        }
    }

    private object FallbackEmptyIteratorSerializer : Serializer<Iterator<*>>() {
        override fun write(kryo: Kryo, output: Output, obj: Iterator<*>) {
            val hasNext = obj.hasNext()
            output.writeBoolean(hasNext)
        }

        override fun read(kryo: Kryo, input: Input, type: Class<out Iterator<*>>): Iterator<*> {
            val hasNext = input.readBoolean()
            if (hasNext) {
                throw UnsupportedOperationException("Restoring checkpoints containing iterators is not supported in this test environment. " +
                        "If you wish to restore these checkpoints in your tests then use the out-of-process node driver, or add " +
                        "--add-opens=${type.fullyQualifiedPackage}=ALL-UNNAMED to the test JVM args.")
            } else {
                // If the iterator didn't have any elements left (which can happen commonly when iterating over a singleton collection) then
                // there's no need to make a fuss. We can return an empty iterator and move on.
                return Collections.emptyIterator<Any>()
            }
        }
    }
}
