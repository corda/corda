package net.corda.core

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CollectionExtensionTests {
    @Test
    fun `noneOrSingle returns a single item`() {
        val collection = listOf(1)
        assertEquals(collection.noneOrSingle(), 1)
        assertEquals(collection.noneOrSingle { it == 1 }, 1)
    }

    @Test
    fun `noneOrSingle returns null if item not found`() {
        val collection = emptyList<Int>()
        assertEquals(collection.noneOrSingle(), null)
    }

    @Test
    fun `noneOrSingle throws if more than one item found`() {
        val collection = listOf(1, 2)
        assertFailsWith<IllegalArgumentException> { collection.noneOrSingle() }
        assertFailsWith<IllegalArgumentException> { collection.noneOrSingle { it > 0 } }
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
}
