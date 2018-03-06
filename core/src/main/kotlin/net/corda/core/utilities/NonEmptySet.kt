/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.utilities

import java.util.*
import java.util.function.Consumer
import java.util.stream.Stream

/**
 * An immutable ordered non-empty set.
 */
class NonEmptySet<T> private constructor(private val elements: Set<T>) : Set<T> by elements {
    companion object {
        /**
         * Returns a singleton set containing [element]. This behaves the same as [Collections.singleton] but returns a
         * [NonEmptySet] for the extra type-safety.
         */
        @JvmStatic
        fun <T> of(element: T): NonEmptySet<T> = NonEmptySet(Collections.singleton(element))

        /** Returns a non-empty set containing the given elements, minus duplicates, in the order each was specified. */
        @JvmStatic
        fun <T> of(first: T, second: T, vararg rest: T): NonEmptySet<T> {
            val elements = LinkedHashSet<T>(rest.size + 2)
            elements += first
            elements += second
            elements.addAll(rest)
            return NonEmptySet(elements)
        }

        /**
         * Returns a non-empty set containing each of [elements], minus duplicates, in the order each appears first in
         * the source collection.
         * @throws IllegalArgumentException If [elements] is empty.
         */
        @JvmStatic
        fun <T> copyOf(elements: Collection<T>): NonEmptySet<T> {
            if (elements is NonEmptySet) return elements
            return when (elements.size) {
                0 -> throw IllegalArgumentException("elements is empty")
                1 -> of(elements.first())
                else -> {
                    val copy = LinkedHashSet<T>(elements.size)
                    elements.forEach { copy += it }  // Can't use Collection.addAll as it doesn't specify insertion order
                    NonEmptySet(copy)
                }
            }
        }
    }

    /** Returns the first element of the set. */
    fun head(): T = elements.iterator().next()

    override fun isEmpty(): Boolean = false
    override fun iterator() = object : Iterator<T> by elements.iterator() {}

    // Following methods are not delegated by Kotlin's Class delegation
    override fun forEach(action: Consumer<in T>) = elements.forEach(action)

    override fun stream(): Stream<T> = elements.stream()
    override fun parallelStream(): Stream<T> = elements.parallelStream()
    override fun spliterator(): Spliterator<T> = elements.spliterator()
    override fun equals(other: Any?): Boolean = other === this || other == elements
    override fun hashCode(): Int = elements.hashCode()
    override fun toString(): String = elements.toString()
}
