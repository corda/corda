package com.r3corda.core.utilities

/**
 * A set which is constrained to ensure it can never be empty. An initial value must be provided at
 * construction, and attempting to remove the last element will cause an IllegalStateException.
 */
class NonEmptySet<T>(initial: T, private val set: MutableSet<T> = mutableSetOf()) : MutableSet<T> {
    init {
        require (set.isEmpty()) { "Provided set must be empty." }
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

    override fun iterator(): MutableIterator<T> = Iterator<T>(set.iterator())

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
                set.equals(other)
            else
                false

    override fun hashCode(): Int = set.hashCode()
    override fun toString(): String = set.toString()

    inner class Iterator<T>(val iterator: MutableIterator<T>) : MutableIterator<T> {
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
    val set = NonEmptySet<T>(initial)
    // We add the first element twice, but it's a set, so who cares
    set.addAll(elements)
    return set
}