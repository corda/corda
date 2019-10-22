package net.corda.serialization.djvm.deserializers

import java.util.*
import java.util.function.Function

class CreateMap : Function<Array<Any>, Map<Any?, Any?>> {
    private val concreteConstructors: Map<Class<out Map<*, *>>, (Array<Array<Any>>) -> Map<Any?, Any?>> = mapOf(
        Map::class.java to ::createMap,
        SortedMap::class.java to ::createSortedMap,
        LinkedHashMap::class.java to ::createLinkedHashMap,
        NavigableMap::class.java to ::createNavigableMap,
        TreeMap::class.java to ::createTreeMap,
        EnumMap::class.java to ::createEnumMap
    )

    private fun createMap(values: Array<Array<Any>>): Map<Any?, Any?> {
        return Collections.unmodifiableMap(values.map { it[0] to it[1] }.toMap())
    }

    private fun createSortedMap(values: Array<Array<Any>>): SortedMap<Any?, out Any?> {
        return Collections.unmodifiableSortedMap(createTreeMap(values))
    }

    private fun createNavigableMap(values: Array<Array<Any>>): NavigableMap<Any?, out Any?> {
        return Collections.unmodifiableNavigableMap(createTreeMap(values))
    }

    private fun createLinkedHashMap(values: Array<Array<Any>>): LinkedHashMap<Any?, out Any?> {
        return values.map { it[0] to it[1] }.toMap(LinkedHashMap())
    }

    private fun createTreeMap(values: Array<Array<Any>>): TreeMap<Any?, out Any?> {
        return values.map { it[0] to it[1] }.toMap(TreeMap())
    }

    private fun createEnumMap(values: Array<Array<Any>>): Map<Any?, Any?> {
        val map = values.map { it[0] to it[1] }.toMap()
        @Suppress("unchecked_cast")
        return EnumMap(map as Map<JustForCasting, Any?>) as Map<Any?, Any?>
    }

    @Suppress("unchecked_cast")
    override fun apply(inputs: Array<Any>): Map<Any?, Any?> {
        val mapClass = inputs[0] as Class<out Map<Any?, Any?>>
        val args = inputs[1] as Array<Array<Any>>
        return concreteConstructors[mapClass]?.invoke(args)!!
    }
}
