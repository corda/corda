package net.corda.core.utilities

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import java.util.*

/**
 * A set which is constrained to ensure it can never be empty. An initial value must be provided at
 * construction, and attempting to remove the last element will cause an IllegalStateException.
 * The underlying set is exposed for Kryo to access, but should not be accessed directly.
 */
class NonEmptySet<T>(initial: T) : MutableSet<T> {
    private val set: MutableSet<T> = HashSet()

    init {
        set.add(initial)
    }

    override val size: Int
        get() = set.size

    override fun add(element: T): Boolean = set.add(element)
    override fun addAll(elements: Collection<T>): Boolean = set.addAll(elements)
    override fun clear() = throw UnsupportedOperationException()
    override fun contains(element: T): Boolean = set.contains(element)
    override fun containsAll(elements: Collection<T>): Boolean = set.containsAll(elements)
    override fun isEmpty(): Boolean = false

    override fun iterator(): MutableIterator<T> = Iterator(set.iterator())

    override fun remove(element: T): Boolean =
            // Test either there's more than one element, or the removal is a no-op
            if (size > 1)
                set.remove(element)
            else if (!contains(element))
                false
            else
                throw IllegalStateException()

    override fun removeAll(elements: Collection<T>): Boolean =
            if (size > elements.size)
                set.removeAll(elements)
            else if (!containsAll(elements))
                // Remove the common elements
                set.removeAll(elements)
            else
                throw IllegalStateException()

    override fun retainAll(elements: Collection<T>): Boolean {
        val iterator = iterator()
        val ret = false

        // The iterator will throw an IllegalStateException if we try removing the last element
        while (iterator.hasNext()) {
            if (!elements.contains(iterator.next())) {
                iterator.remove()
            }
        }

        return ret
    }

    override fun equals(other: Any?): Boolean =
            if (other is Set<*>)
                // Delegate down to the wrapped set's equals() function
                set == other
            else
                false

    override fun hashCode(): Int = set.hashCode()
    override fun toString(): String = set.toString()

    inner class Iterator<out T>(val iterator: MutableIterator<T>) : MutableIterator<T> {
        override fun hasNext(): Boolean = iterator.hasNext()
        override fun next(): T = iterator.next()
        override fun remove() =
                if (set.size > 1)
                    iterator.remove()
                else
                    throw IllegalStateException()
    }
}

fun <T> nonEmptySetOf(initial: T, vararg elements: T): NonEmptySet<T> {
    val set = NonEmptySet(initial)
    // We add the first element twice, but it's a set, so who cares
    set.addAll(elements)
    return set
}

/**
 * Custom serializer which understands it has to read in an item before
 * trying to construct the set.
 */
object NonEmptySetSerializer : Serializer<NonEmptySet<Any>>() {
    override fun write(kryo: Kryo, output: Output, obj: NonEmptySet<Any>) {
        // Write out the contents as normal
        output.writeInt(obj.size)
        obj.forEach { kryo.writeClassAndObject(output, it) }
    }

    override fun read(kryo: Kryo, input: Input, type: Class<NonEmptySet<Any>>): NonEmptySet<Any> {
        val size = input.readInt()
        require(size >= 1) { "Size is positive" }
        // TODO: Is there an upper limit we can apply to how big one of these could be?
        val first = kryo.readClassAndObject(input)
        // Read the first item and use it to construct the NonEmptySet
        val set = NonEmptySet(first)
        // Read in the rest of the set
        for (i in 2..size) {
            set.add(kryo.readClassAndObject(input))
        }
        return set
    }
}
