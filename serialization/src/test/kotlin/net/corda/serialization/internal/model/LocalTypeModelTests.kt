package net.corda.serialization.internal.model

import com.google.common.reflect.TypeToken
import net.corda.core.serialization.SerializableCalculatedProperty
import net.corda.serialization.internal.AllWhitelist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Type
import java.time.LocalDateTime
import java.util.*

class LocalTypeModelTests {

    private val model = ConfigurableLocalTypeModel(WhitelistBasedTypeModelConfiguration(AllWhitelist))

    interface CollectionHolder<K, V> {
        val list: List<V>
        val map: Map<K, V>
        val array: Array<List<V>>
    }

    open class StringKeyedCollectionHolder<T>(override val list: List<T>, override val map: Map<String, T>, override val array: Array<List<T>>) : CollectionHolder<String, T>

    class StringCollectionHolder(list: List<String>, map: Map<String, String>, array: Array<List<String>>) : StringKeyedCollectionHolder<String>(list, map, array)

    class Nested(val collectionHolder: StringKeyedCollectionHolder<out Int>?)

    @Test
    fun `Primitives and collections`() {
        assertInformation<CollectionHolder<UUID, LocalDateTime>>("CollectionHolder<UUID, LocalDateTime>")

        assertInformation<StringKeyedCollectionHolder<Int>>("""
            StringKeyedCollectionHolder<Integer>(list: List<Integer>, map: Map<String, Integer>, array: List<Integer>[]): CollectionHolder<String, Integer>
              array: List<Integer>[]
              list: List<Integer>
              map: Map<String, Integer>
         """)

        assertInformation<StringCollectionHolder>("""
            StringCollectionHolder(list: List<String>, map: Map<String, String>, array: List<String>[]): StringKeyedCollectionHolder<String>, CollectionHolder<String, String>
              array: List<String>[]
              list: List<String>
              map: Map<String, String>
        """)

        assertInformation<Nested>("""
            Nested(collectionHolder: StringKeyedCollectionHolder<Integer>?)
              collectionHolder (optional): StringKeyedCollectionHolder<Integer>(list: List<Integer>, map: Map<String, Integer>, array: List<Integer>[]): CollectionHolder<String, Integer>
              array: List<Integer>[]
              list: List<Integer>
              map: Map<String, Integer>
        """)
    }

    interface SuperSuper<A, B> {
        val a: A
        val b: B
    }

    interface Super<C> : SuperSuper<C, String> {
        val c: List<C>
    }

    abstract class Abstract<T>(override val a: Array<T>, override val b: String) : Super<Array<T>>

    class Concrete(a: Array<Int>, b: String, override val c: List<Array<Int>>) : Abstract<Int>(a, b)

    @Test
    fun `interfaces and superclasses`() {
        assertInformation<SuperSuper<Int, Int>>("SuperSuper<Integer, Integer>")
        assertInformation<Super<UUID>>("Super<UUID>: SuperSuper<UUID, String>")
        assertInformation<Abstract<LocalDateTime>>("""
            Abstract<LocalDateTime>: Super<LocalDateTime[]>, SuperSuper<LocalDateTime[], String>
              a: LocalDateTime[]
              b: String
        """)
        assertInformation<Concrete>("""
            Concrete(a: Integer[], b: String, c: List<Integer[]>): Abstract<Integer>, Super<Integer[]>, SuperSuper<Integer[], String>
              a: Integer[]
              b: String
              c: List<Integer[]>
        """)
    }

    interface OldStylePojo<A> {
        var a: A?
        var b: String
        @get:SerializableCalculatedProperty
        val c: String
    }

    class OldStylePojoImpl : OldStylePojo<IntArray> {
        override var a: IntArray? = null
        override var b: String = ""
        override val c: String = a.toString() + b
    }

    @Test
    fun `getter setter and calculated properties`() {
        assertInformation<OldStylePojoImpl>("""
           OldStylePojoImpl(): OldStylePojo<int[]>
             a (optional): int[]
             b: String
             c (calculated): String
        """)
    }

    class AliasingOldStylePojoImpl(override var a: String?, override var b: String, override val c: String): OldStylePojo<String>

    @Test
    fun `calculated properties aliased by fields in implementing classes`() {
        assertInformation<AliasingOldStylePojoImpl>("""
           AliasingOldStylePojoImpl(a: String?, b: String, c: String): OldStylePojo<String>
             a (optional): String
             b: String
             c: String
        """)
    }

    class TransitivelyNonComposable(val a: String, val b: Exception)

    @Test
    fun `non-composable types`() {
        val modelWithoutOpacity = ConfigurableLocalTypeModel(WhitelistBasedTypeModelConfiguration(AllWhitelist) { false })
        assertTrue(modelWithoutOpacity.inspect(typeOf<Exception>()) is LocalTypeInformation.NonComposable)
        assertTrue(modelWithoutOpacity.inspect(typeOf<TransitivelyNonComposable>()) is LocalTypeInformation.NonComposable)
    }

    private inline fun <reified T> assertInformation(expected: String) {
        assertEquals(expected.trimIndent(), model.inspect(typeOf<T>()).prettyPrint())
    }

    /**
     * Handy for seeing what the inspector/pretty printer actually outputs for a type
     */
    @Suppress("unused")
    private inline fun <reified T> printInformation() {
        println(model.inspect(typeOf<T>()).prettyPrint())
    }

    private inline fun <reified T> typeOf(): Type = object : TypeToken<T>() {}.type
}