package net.corda.serialization.djvm.deserializers

import java.util.Collections.unmodifiableMap
import java.util.Collections.unmodifiableNavigableMap
import java.util.Collections.unmodifiableSortedMap
import java.util.EnumMap
import java.util.NavigableMap
import java.util.SortedMap
import java.util.TreeMap
import java.util.function.Function

class CreateMap : Function<Array<out Any>, Map<Any?, Any?>> {
    private val concreteConstructors: Map<Class<out Map<*, *>>, (Array<Array<out Any>>) -> Map<Any?, Any?>> = mapOf(
        Map::class.java to ::createMap,
        SortedMap::class.java to ::createSortedMap,
        LinkedHashMap::class.java to ::createLinkedHashMap,
        NavigableMap::class.java to ::createNavigableMap,
        TreeMap::class.java to ::createTreeMap,
        EnumMap::class.java to ::createEnumMap
    )

    private fun createMap(values: Array<Array<out Any>>): Map<Any?, Any?> {
        return unmodifiableMap(values.associate { it[0] to it[1] })
    }

    private fun createSortedMap(values: Array<Array<out Any>>): SortedMap<Any?, out Any?> {
        return unmodifiableSortedMap(createTreeMap(values))
    }

    private fun createNavigableMap(values: Array<Array<out Any>>): NavigableMap<Any?, out Any?> {
        return unmodifiableNavigableMap(createTreeMap(values))
    }

    private fun createLinkedHashMap(values: Array<Array<out Any>>): LinkedHashMap<Any?, out Any?> {
        return values.associateTo(LinkedHashMap()) { it[0] to it[1] }
    }

    private fun createTreeMap(values: Array<Array<out Any>>): TreeMap<Any?, out Any?> {
        return values.associateTo(TreeMap()) { it[0] to it[1] }
    }

    private fun createEnumMap(values: Array<Array<out Any>>): Map<Any?, Any?> {
        val map = values.associate { it[0] to it[1] }
        @Suppress("unchecked_cast")
        return EnumMap(map as Map<JustForCasting, Any?>) as Map<Any?, Any?>
    }

    @Suppress("unchecked_cast")
    override fun apply(inputs: Array<out Any>): Map<Any?, Any?> {
        val mapClass = inputs[0] as Class<out Map<Any?, Any?>>
        val args = inputs[1] as Array<Array<out Any>>
        return concreteConstructors[mapClass]?.invoke(args)!!
    }
}
