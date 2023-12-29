package net.corda.nodeapi.internal.serialization.kryo

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import java.util.LinkedList

/**
 * The [LinkedHashMap] and [LinkedHashSet] have a problem with the default Quasar/Kryo serialisation
 * in that serialising an iterator (and subsequent [LinkedHashMap.Entry]) over a sufficiently large
 * data set can lead to a stack overflow (because the object map is traversed recursively).
 *
 * We've added our own custom serializer in order to ensure that only the key/value are recorded.
 * The rest of the list isn't required at this scope.
 */
object LinkedHashMapEntrySerializer : Serializer<Map.Entry<*, *>>() {
    // Create a dummy map so that we can get the LinkedHashMap$Entry from it
    // The element type of the map doesn't matter.  The entry is all we want
    private val constructor = linkedMapOf(1L to 1).entries.first()::class.java.declaredConstructors.single().apply { isAccessible = true }

    /**
     * Kryo would end up serialising "this" entry, then serialise "this.after" recursively, leading to a very large stack.
     * we'll skip that and just write out the key/value
     */
    override fun write(kryo: Kryo, output: Output, obj: Map.Entry<*, *>) {
        val e: Map.Entry<*, *> = obj
        kryo.writeClassAndObject(output, e.key)
        kryo.writeClassAndObject(output, e.value)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<out Map.Entry<*, *>>): Map.Entry<*, *> {
        val key = kryo.readClassAndObject(input)
        val value = kryo.readClassAndObject(input)
        return constructor.newInstance(0, key, value, null) as Map.Entry<*, *>
    }
}

/**
 * Also, add a [ListIterator] serializer to avoid more linked list issues.
*/
object LinkedListItrSerializer : Serializer<ListIterator<Any>>() {
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


