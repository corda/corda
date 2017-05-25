package net.corda.core

import org.junit.Assert
import org.junit.Assert.assertArrayEquals
import org.junit.Test
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
    }

    @Test
    fun `Stream toTypedArray works`() {
        val a: Array<String> = Stream.of("one", "two").toTypedArray()
        assertEquals(Array<String>::class.java, a.javaClass)
        Assert.assertArrayEquals(arrayOf("one", "two"), a)
        val b: Array<String?> = Stream.of("one", "two", null).toTypedArray()
        assertEquals(Array<String?>::class.java, b.javaClass)
        Assert.assertArrayEquals(arrayOf("one", "two", null), b)
    }
}
