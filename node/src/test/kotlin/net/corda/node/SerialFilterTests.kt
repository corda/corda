package net.corda.node

import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class SerialFilterTests {
    @Test
    fun `null and primitives are accepted and arrays are unwrapped`() {
        val acceptClass = { _: Class<*> -> fail("Should not be invoked.") }
        listOf(null, Byte::class.javaPrimitiveType, IntArray::class.java, Array<CharArray>::class.java).forEach {
            assertTrue(SerialFilter.applyPredicate(acceptClass, it))
        }
    }

    @Test
    fun `the predicate is applied to the componentType`() {
        val classes = mutableListOf<Class<*>>()
        val acceptClass = { clazz: Class<*> ->
            classes.add(clazz)
            false
        }
        listOf(String::class.java, Array<Unit>::class.java, Array<Array<IOException>>::class.java).forEach {
            assertFalse(SerialFilter.applyPredicate(acceptClass, it))
        }
        assertEquals(listOf<Class<*>>(String::class.java, Unit::class.java, IOException::class.java), classes)
    }
}
