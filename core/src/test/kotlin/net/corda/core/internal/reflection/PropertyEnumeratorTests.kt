package net.corda.core.internal.reflection

import net.corda.core.internal.reflection.ObjectGraphTraverser
import org.junit.Test
import java.lang.reflect.Type
import kotlin.test.assertEquals

class PropertyEnumeratorTests {

    @Test
    fun traversesObjectGraphDepthFirst() {
        data class Bottom(var value: Any?) {
            override fun hashCode(): Int = when(value) {
                is String -> value!!.hashCode()
                else -> 0
            }

            override fun toString(): String = when(value) {
                is String -> "Bottom($value)"
                else -> "cycle"
            }
        }

        data class Middle(val value: String, val bottomA: Bottom, val bottomB: Bottom)
        data class Top(val value: String, val middleA: Middle, val middleB: Middle)

        val bottomAA = Bottom("aa")
        val bottomAB = Bottom("ab")
        val bottomBA = Bottom("ba")
        val bottomBB = Bottom(null)
        val middleA = Middle("middle a", bottomAA, bottomAB)
        val middleB = Middle("middle b", bottomBA, bottomBB)
        val top = Top("top", middleA, middleB)

        // create a cycle
        bottomBB.value = top

        val objectsInGraph = ObjectGraphTraverser.traverse(top).toList()
        val expected = listOf(
                "aa", bottomAA, "ab", bottomAB, "middle a", middleA, "ba", bottomBA, bottomBB, "middle b", middleB, "top", top)

        assertEquals(expected, objectsInGraph)
    }
}