/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.internal

import net.corda.core.contracts.TimeWindow
import net.corda.core.crypto.SecureHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.util.*
import java.util.stream.IntStream
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

open class InternalUtilsTest {
    @Test
    fun `noneOrSingle on an empty collection`() {
        val collection = emptyList<Int>()
        assertThat(collection.noneOrSingle()).isNull()
        assertThat(collection.noneOrSingle { it == 1 }).isNull()
    }

    @Test
    fun `noneOrSingle on a singleton collection`() {
        val collection = listOf(1)
        assertThat(collection.noneOrSingle()).isEqualTo(1)
        assertThat(collection.noneOrSingle { it == 1 }).isEqualTo(1)
        assertThat(collection.noneOrSingle { it == 2 }).isNull()
    }

    @Test
    fun `noneOrSingle on a collection with two items`() {
        val collection = listOf(1, 2)
        assertFailsWith<IllegalArgumentException> { collection.noneOrSingle() }
        assertThat(collection.noneOrSingle { it == 1 }).isEqualTo(1)
        assertThat(collection.noneOrSingle { it == 2 }).isEqualTo(2)
        assertThat(collection.noneOrSingle { it == 3 }).isNull()
        assertFailsWith<IllegalArgumentException> { collection.noneOrSingle { it > 0 } }
    }

    @Test
    fun `noneOrSingle on a collection with items 1, 2, 1`() {
        val collection = listOf(1, 2, 1)
        assertFailsWith<IllegalArgumentException> { collection.noneOrSingle() }
        assertFailsWith<IllegalArgumentException> { collection.noneOrSingle { it == 1 } }
        assertThat(collection.noneOrSingle { it == 2 }).isEqualTo(2)
    }

    @Test
    fun `indexOfOrThrow returns index of the given item`() {
        val collection = listOf(1, 2)
        assertEquals(collection.indexOfOrThrow(1), 0)
        assertEquals(collection.indexOfOrThrow(2), 1)
    }

    @Test
    fun `indexOfOrThrow throws if the given item is not found`() {
        val collection = listOf(1)
        assertFailsWith<IllegalArgumentException> { collection.indexOfOrThrow(2) }
    }

    @Test
    fun `IntProgression stream works`() {
        assertArrayEquals(intArrayOf(1, 2, 3, 4), (1..4).stream().toArray())
        assertArrayEquals(intArrayOf(1, 2, 3, 4), (1 until 5).stream().toArray())
        assertArrayEquals(intArrayOf(1, 3), (1..4 step 2).stream().toArray())
        assertArrayEquals(intArrayOf(1, 3), (1..3 step 2).stream().toArray())
        @Suppress("EmptyRange") // It's supposed to be empty.
        assertArrayEquals(intArrayOf(), (1..0).stream().toArray())
        assertArrayEquals(intArrayOf(1, 0), (1 downTo 0).stream().toArray())
        assertArrayEquals(intArrayOf(3, 1), (3 downTo 0 step 2).stream().toArray())
        assertArrayEquals(intArrayOf(3, 1), (3 downTo 1 step 2).stream().toArray())
    }

    @Test
    fun `IntProgression spliterator characteristics and comparator`() {
        val rangeCharacteristics = IntStream.range(0, 2).spliterator().characteristics()
        val forward = (0..9 step 3).stream().spliterator()
        assertEquals(rangeCharacteristics, forward.characteristics())
        assertEquals(null, forward.comparator)
        val reverse = (9 downTo 0 step 3).stream().spliterator()
        assertEquals(rangeCharacteristics, reverse.characteristics())
        assertEquals(Comparator.reverseOrder(), reverse.comparator)
    }

    @Test
    fun `Stream toTypedArray works`() {
        val a: Array<String> = Stream.of("one", "two").toTypedArray()
        assertEquals(Array<String>::class.java, a.javaClass)
        assertArrayEquals(arrayOf("one", "two"), a)
        val b: Array<String?> = Stream.of("one", "two", null).toTypedArray()
        assertEquals(Array<String?>::class.java, b.javaClass)
        assertArrayEquals(arrayOf("one", "two", null), b)
    }

    @Test
    fun kotlinObjectInstance() {
        assertThat(PublicObject::class.java.kotlinObjectInstance).isSameAs(PublicObject)
        assertThat(PrivateObject::class.java.kotlinObjectInstance).isSameAs(PrivateObject)
        assertThat(ProtectedObject::class.java.kotlinObjectInstance).isSameAs(ProtectedObject)
        assertThat(TimeWindow::class.java.kotlinObjectInstance).isNull()
        assertThat(PrivateClass::class.java.kotlinObjectInstance).isNull()
    }

    @Test
    fun `test SHA-256 hash for InputStream`() {
        val contents = arrayOfJunk(DEFAULT_BUFFER_SIZE * 2 + DEFAULT_BUFFER_SIZE / 2)
        assertThat(contents.inputStream().hash())
            .isEqualTo(SecureHash.parse("A4759E7AA20338328866A2EA17EAF8C7FE4EC6BBE3BB71CEE7DF7C0461B3C22F"))
    }

    private fun arrayOfJunk(size: Int) = ByteArray(size).apply {
        for (i in 0 until size) {
            this[i] = (i and 0xFF).toByte()
        }
    }

    object PublicObject
    private object PrivateObject
    protected object ProtectedObject

    private class PrivateClass
}
