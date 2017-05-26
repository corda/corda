package net.corda.core

import java.util.*
import java.util.Spliterator.*
import java.util.stream.IntStream
import java.util.stream.Stream
import java.util.stream.StreamSupport
import kotlin.streams.asSequence

private fun IntProgression.spliteratorOfInt(): Spliterator.OfInt {
    val kotlinIterator = iterator()
    val javaIterator = object : PrimitiveIterator.OfInt {
        override fun nextInt() = kotlinIterator.nextInt()
        override fun hasNext() = kotlinIterator.hasNext()
        override fun remove() = throw UnsupportedOperationException("remove")
    }
    val spliterator = Spliterators.spliterator(
            javaIterator,
            (1 + (last - first) / step).toLong(),
            SUBSIZED or IMMUTABLE or NONNULL or SIZED or ORDERED or SORTED or DISTINCT
    )
    return if (step > 0) spliterator else object : Spliterator.OfInt by spliterator {
        override fun getComparator() = Comparator.reverseOrder<Int>()
    }
}

fun IntProgression.stream(): IntStream = StreamSupport.intStream(spliteratorOfInt(), false)

@Suppress("UNCHECKED_CAST") // When toArray has filled in the array, the component type is no longer T? but T (that may itself be nullable).
inline fun <reified T> Stream<T>.toTypedArray() = toArray { size -> arrayOfNulls<T>(size) } as Array<T>

fun <T> Stream<T>.singleOrNull() = asSequence().singleOrNull()
