package net.corda.core

import java.util.*
import java.util.stream.IntStream
import java.util.stream.Stream
import java.util.stream.StreamSupport

/**
 * @see [kotlin.streams.asStream]
 */
private fun IntProgression.spliteratorOfInt(): Spliterator.OfInt {
    val kotlinIterator = iterator()
    val javaIterator = object : PrimitiveIterator.OfInt {
        override fun nextInt() = kotlinIterator.nextInt()
        override fun hasNext() = kotlinIterator.hasNext()
        override fun remove() = throw UnsupportedOperationException("remove")
    }
    return Spliterators.spliterator(javaIterator, (1 + (last - first) / step).toLong(), Spliterator.ORDERED)
}

/**
 * @see [kotlin.streams.asStream]
 */
fun IntProgression.stream(): IntStream = StreamSupport.intStream({ spliteratorOfInt() }, Spliterator.ORDERED, false)

@Suppress("UNCHECKED_CAST") // When toArray has filled in the array, the component type is no longer T? but T (that may itself be nullable).
inline fun <reified T> Stream<T>.toTypedArray() = toArray { size -> arrayOfNulls<T>(size) } as Array<T>
