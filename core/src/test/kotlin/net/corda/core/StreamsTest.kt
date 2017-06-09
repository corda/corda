package net.corda.core

import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.util.stream.IntStream
import java.util.stream.Stream
import kotlin.test.assertEquals

class StreamsTest {
    @Test
    fun `IntProgression stream works`() {
        assertArrayEquals(intArrayOf(1, 2, 3, 4), (1..4).stream().toArray())
        assertArrayEquals(intArrayOf(1, 2, 3, 4), (1 until 5).stream().toArray())
        assertArrayEquals(intArrayOf(1, 3), (1..4 step 2).stream().toArray())
        assertArrayEquals(intArrayOf(1, 3), (1..3 step 2).stream().toArray())
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
}
