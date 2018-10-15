package net.corda.serialization.internal.model

import com.google.common.reflect.TypeToken
import net.corda.serialization.internal.AllWhitelist
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Type
import java.time.LocalDateTime
import java.util.*

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
        val model = LocalTypeModel(WhitelistBasedTypeModelConfiguration(AllWhitelist))

        assertEquals("CollectionHolder<UUID, LocalDateTime>",
                model.inspect(typeOf<CollectionHolder<UUID, LocalDateTime>>()).prettyPrint())

        assertEquals(
                """
                StringKeyedCollectionHolder<Integer>: CollectionHolder<String, Integer>
                  array: List<Integer>[]
                  map: Map<String, Integer>
                  list: List<Integer>
                """.trimIndent(), model.inspect(typeOf<StringKeyedCollectionHolder<Int>>()).prettyPrint()
        )

        assertEquals(
                """
                Nested
                  collectionHolders: StringKeyedCollectionHolder<Integer>[]
                """.trimIndent(),
                model.inspect(classOf<Nested>()).prettyPrint())
    }

    private inline fun <reified T> typeOf(): Type = object : TypeToken<T>() {}.type
    private inline fun <reified T> classOf(): Class<*> = T::class.java
}