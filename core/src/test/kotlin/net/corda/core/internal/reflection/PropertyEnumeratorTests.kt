package net.corda.core.internal.reflection

import org.junit.Test
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

        data class Middle(val value: String, val bottoms: List<Bottom>)
        data class Top(val value: String, val middles: Map<String, Middle>)

        val bottomAA = Bottom("aa")
        val bottomAB = Bottom("ab")
        val bottomBA = Bottom("ba")
        val bottomBB = Bottom(null)
        val middleA = Middle("middle a", listOf(bottomAA, bottomAB))
        val middleB = Middle("middle b", listOf(bottomBA, bottomBB))
        val top = Top("top", mapOf("a" to middleA, "b" to middleB))

        // create a cycle
        bottomBB.value = top

        val objectsInGraph = ObjectGraphTraverser.traverse(top).toList()
        val expected = listOf(
                "a", "aa", bottomAA, "ab", bottomAB, "middle a", middleA,
                "b", "ba", bottomBA, bottomBB, "middle b", middleB,
                "top", top)

        assertEquals(expected, objectsInGraph)
    }
}