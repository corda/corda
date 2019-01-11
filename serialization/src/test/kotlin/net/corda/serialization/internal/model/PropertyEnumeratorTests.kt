package net.corda.serialization.internal.model

import org.junit.Test
import java.lang.reflect.Type
import kotlin.test.assertEquals

class PropertyEnumeratorTests {

    private val configuration = object : LocalTypeModelConfiguration {
        override fun isOpaque(type: Type): Boolean = false
        override fun isExcluded(type: Type): Boolean = false
    }

    private val typeModel = ConfigurableLocalTypeModel(configuration)

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

        val objectsInGraph = ObjectGraphTraverser(typeModel::inspect).traverseGraph(top).toList()
        val expected = listOf(
                "aa", bottomAA, "ab", bottomAB, "middle a", middleA, "ba", bottomBA, bottomBB, "middle b", middleB, "top", top)

        assertEquals(expected, objectsInGraph)
    }
}