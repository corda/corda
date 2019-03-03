package net.corda.core.utilities

import net.corda.core.contracts.ComponentGroupEnum.*
import net.corda.core.internal.lazyMapped
import net.corda.core.internal.LazyMappedList
import net.corda.core.internal.TransactionDeserialisationException
import net.corda.core.serialization.MissingAttachmentsException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import kotlin.test.assertEquals

class LazyMappedListTest {

    @get:Rule
    val exception: ExpectedException = ExpectedException.none()

    @Test
    fun `LazyMappedList works`() {
        val originalList = (1 until 10).toList()

        var callCounter = 0

        val lazyList = originalList.lazyMapped { value, _ ->
            callCounter++
            value * value
        }

        // No transform called when created.
        assertEquals(0, callCounter)

        // No transform called when calling 'size'.
        assertEquals(9, lazyList.size)
        assertEquals(0, callCounter)

        // Called once when getting an element.
        assertEquals(16, lazyList[3])
        assertEquals(1, callCounter)

        // Not called again when getting the same element.
        assertEquals(16, lazyList[3])
        assertEquals(1, callCounter)
    }

    @Test
    fun testMissingAttachments() {
        exception.expect(MissingAttachmentsException::class.java)
        exception.expectMessage("Uncatchable!")

        @Suppress("unchecked_cast")
        val lazyList = (0 until 5).toList().lazyMapped<Int, Int> { _, _ ->
            throw MissingAttachmentsException(emptyList(), "Uncatchable!")
        } as LazyMappedList<Int, Int>

        lazyList.eager { _, _ -> -999 }
    }

    @Test
    fun testDeserialisationExceptions() {
        @Suppress("unchecked_cast")
        val lazyList = (0 until 5).toList().lazyMapped<Int, Int> { _, index ->
            throw TransactionDeserialisationException(
                OUTPUTS_GROUP, index, IllegalStateException("Catch this!"))
        } as LazyMappedList<Int, Int>

        lazyList.eager { _, _ -> -999 }
        assertEquals(5, lazyList.size)
        lazyList.forEachIndexed { idx, item ->
            assertEquals(-999, item, "Item[$idx] mismatch")
        }
    }
}
