package net.corda.serialization.internal.model

import org.junit.Test

class TypeModelTests {

    interface CollectionHolder<K, V> {
        val list: List<V>
        val map: Map<K, V>
        val array: Array<List<V>>
    }

    open class StringKeyedCollectionHolder<T>(override val list: List<T>, override val map: Map<String, T>, override val array: Array<List<T>>): CollectionHolder<String, T>

    class StringCollectionHolder(list: List<String>, map: Map<String, String>, array: Array<List<String>>): StringKeyedCollectionHolder<String>(list, map, array)

    class Nested(val collectionHolders: Array<StringKeyedCollectionHolder<out Int>>)

    @Test
    fun `Primitives and collections`() {
        val model = LocalTypeModel()
        model.interpret(classOf<Nested>())

        model.knownIdentifiers.asSequence().mapNotNull { model[it]?.prettyPrint() }
                .sorted()
                .forEach { println(it) }
    }

    private inline fun <reified T> classOf(): Class<*> = T::class.java
}