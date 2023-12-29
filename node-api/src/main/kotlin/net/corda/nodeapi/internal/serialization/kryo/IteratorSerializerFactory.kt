package net.corda.nodeapi.internal.serialization.kryo

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.SerializerFactory
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import java.util.LinkedList

object IteratorSerializerFactory : SerializerFactory.BaseSerializerFactory<Serializer<out Any>>() {
    private val linkedListListIteratorClass = LinkedList<Any>().listIterator()::class.java

    override fun newSerializer(kryo: Kryo, type: Class<*>): Serializer<out Any> {
        return if (type == linkedListListIteratorClass) {
            LinkedListItrSerializer
        } else {
            IteratorSerializer(type, kryo)
        }
    }

    private object LinkedListItrSerializer : Serializer<ListIterator<Any>>() {
        // Create a dummy list so that we can get the ListItr from it
        // The element type of the list doesn't matter.  The iterator is all we want
        private val outerListField = LinkedList<Long>(listOf(1)).listIterator()::class.java.getDeclaredField("this$0").apply { isAccessible = true }

        override fun write(kryo: Kryo, output: Output, obj: ListIterator<Any>) {
            kryo.writeClassAndObject(output, outerListField.get(obj))
            output.writeInt(obj.nextIndex())
        }

        override fun read(kryo: Kryo, input: Input, type: Class<out ListIterator<Any>>): ListIterator<Any> {
            val list = kryo.readClassAndObject(input) as LinkedList<*>
            val index = input.readInt()
            return list.listIterator(index)
        }
    }
}
