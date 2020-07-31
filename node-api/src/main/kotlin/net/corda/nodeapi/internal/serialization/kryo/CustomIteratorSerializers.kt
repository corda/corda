package net.corda.nodeapi.internal.serialization.kryo

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.LinkedList

/**
 * The [LinkedHashMap] and [LinkedHashSet] have a problem with the default Quasar/Kryo serialisation
 * in that serialising an iterator (and subsequent [LinkedHashMap.Entry]) over a sufficiently large
 * data set can lead to a stack overflow (because the object map is traversed recursively).
 *
 * We've added our own custom serializer in order to ensure that the iterator is correctly deserialized.
 */
internal object LinkedHashMapIteratorSerializer : Serializer<Iterator<*>>() {
    private val DUMMY_MAP = linkedMapOf(1L to 1)
    private val outerMapField: Field = getIterator()::class.java.superclass.getDeclaredField("this$0").apply { isAccessible = true }
    private val currentField: Field = getIterator()::class.java.superclass.getDeclaredField("current").apply { isAccessible = true }

    private val KEY_ITERATOR_CLASS: Class<MutableIterator<Long>> = DUMMY_MAP.keys.iterator().javaClass
    private val VALUE_ITERATOR_CLASS: Class<MutableIterator<Int>> = DUMMY_MAP.values.iterator().javaClass
    private val MAP_ITERATOR_CLASS: Class<MutableIterator<MutableMap.MutableEntry<Long, Int>>> = DUMMY_MAP.iterator().javaClass

    fun getIterator(): Any = DUMMY_MAP.iterator()

    override fun write(kryo: Kryo, output: Output, obj: Iterator<*>) {
        val current: Map.Entry<*, *>? = currentField.get(obj) as Map.Entry<*, *>?
        kryo.writeClassAndObject(output, outerMapField.get(obj))
        kryo.writeClassAndObject(output, current)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<Iterator<*>>): Iterator<*> {
        val outerMap = kryo.readClassAndObject(input) as Map<*, *>
        return when (type) {
            KEY_ITERATOR_CLASS -> {
                val current = (kryo.readClassAndObject(input) as? Map.Entry<*, *>)?.key
                outerMap.keys.iterator().returnToIteratorLocation(kryo, current)
            }
            VALUE_ITERATOR_CLASS -> {
                val current = (kryo.readClassAndObject(input) as? Map.Entry<*, *>)?.value
                outerMap.values.iterator().returnToIteratorLocation(kryo, current)
            }
            MAP_ITERATOR_CLASS -> {
                val current = (kryo.readClassAndObject(input) as? Map.Entry<*, *>)
                outerMap.iterator().returnToIteratorLocation(kryo, current)
            }
            else -> throw IllegalStateException("Invalid type")
        }
    }

    private fun Iterator<*>.returnToIteratorLocation(kryo: Kryo, current: Any?): Iterator<*> {
        while (this.hasNext()) {
            val key = this.next()
            if (iteratedObjectsEqual(kryo, key, current)) break
        }
        return this
    }

    private fun iteratedObjectsEqual(kryo: Kryo, a: Any?, b: Any?): Boolean = if (a == null || b == null) {
        a == b
    } else {
        a === b || mapEntriesEqual(kryo, a, b) || kryoOptimisesAwayReferencesButEqual(kryo, a, b)
    }

    /**
     * Kryo can substitute brand new created instances for some types during deserialization, making the identity check fail.
     * Fall back to equality for those.
     */
    private fun kryoOptimisesAwayReferencesButEqual(kryo: Kryo, a: Any, b: Any) =
            (!kryo.referenceResolver.useReferences(a.javaClass) && !kryo.referenceResolver.useReferences(b.javaClass) && a == b)

    private fun mapEntriesEqual(kryo: Kryo, a: Any, b: Any) =
            (a is Map.Entry<*, *> && b is Map.Entry<*, *> && iteratedObjectsEqual(kryo, a.key, b.key))
}

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
    private val DUMMY_MAP = linkedMapOf(1L to 1)
    fun getEntry(): Any = DUMMY_MAP.entries.first()
    private val constr: Constructor<*> = getEntry()::class.java.declaredConstructors.single().apply { isAccessible = true }

    /**
     * Kryo would end up serialising "this" entry, then serialise "this.after" recursively, leading to a very large stack.
     * we'll skip that and just write out the key/value
     */
    override fun write(kryo: Kryo, output: Output, obj: Map.Entry<*, *>) {
        val e: Map.Entry<*, *> = obj
        kryo.writeClassAndObject(output, e.key)
        kryo.writeClassAndObject(output, e.value)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<Map.Entry<*, *>>): Map.Entry<*, *> {
        val key = kryo.readClassAndObject(input)
        val value = kryo.readClassAndObject(input)
        return constr.newInstance(0, key, value, null) as Map.Entry<*, *>
    }
}

/**
 * Also, add a [ListIterator] serializer to avoid more linked list issues.
*/
object LinkedListItrSerializer : Serializer<ListIterator<Any>>() {
    // Create a dummy list so that we can get the ListItr from it
    // The element type of the list doesn't matter.  The iterator is all we want
    private val DUMMY_LIST = LinkedList<Long>(listOf(1))
    fun getListItr(): Any  = DUMMY_LIST.listIterator()

    private val outerListField: Field = getListItr()::class.java.getDeclaredField("this$0").apply { isAccessible = true }

    override fun write(kryo: Kryo, output: Output, obj: ListIterator<Any>) {
        kryo.writeClassAndObject(output, outerListField.get(obj))
        output.writeInt(obj.nextIndex())
    }

    override fun read(kryo: Kryo, input: Input, type: Class<ListIterator<Any>>): ListIterator<Any> {
        val list = kryo.readClassAndObject(input) as LinkedList<*>
        val index = input.readInt()
        return list.listIterator(index)
    }
}


