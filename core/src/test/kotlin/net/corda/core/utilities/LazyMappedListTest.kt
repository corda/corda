package net.corda.core.utilities

import net.corda.core.internal.lazyMapped
import org.junit.Test
import kotlin.test.assertEquals

class LazyMappedListTest {

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

}
