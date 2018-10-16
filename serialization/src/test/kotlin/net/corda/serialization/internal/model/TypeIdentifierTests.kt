package net.corda.serialization.internal.model

import com.google.common.reflect.TypeToken
import net.corda.serialization.internal.model.TypeIdentifier.*
import org.junit.Test
import java.lang.reflect.Type
import kotlin.test.assertEquals

class TypeIdentifierTests {

    @Test
    fun `primitive types and arrays`() {
        assertIdentified(Int::class.javaPrimitiveType!!, "int")
        assertIdentified<Int>("Integer")
        assertIdentified<IntArray>("int[]")
        assertIdentified<Array<Int>>("Integer[]")
    }

    @Test
    fun `erased and unerased`() {
        assertIdentified(List::class.java, "List (erased)")
        assertIdentified<List<Int>>("List<Integer>")
    }

    interface HasArray<T> {
        val array: Array<List<T>>
    }

    class HasStringArray(override val array: Array<List<String>>): HasArray<String>

    @Test
    fun `resolved against an owning type`() {
        val fieldType = HasArray::class.java.getDeclaredMethod("getArray").genericReturnType
        assertIdentified(fieldType, "List<*>[]")

        assertEquals(
                "List<String>[]",
                TypeIdentifier.forGenericType(fieldType, HasStringArray::class.java).prettyPrint())
    }

    private fun assertIdentified(type: Type, expected: String) =
            assertEquals(expected, TypeIdentifier.forGenericType(type).prettyPrint())

    private inline fun <reified T> assertIdentified(expected: String) =
            assertEquals(expected, TypeIdentifier.forGenericType(typeOf<T>()).prettyPrint())

    private inline fun <reified T> typeOf() = object : TypeToken<T>() {}.type
}