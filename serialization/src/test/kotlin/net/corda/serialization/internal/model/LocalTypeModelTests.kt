package net.corda.serialization.internal.model

import com.google.common.reflect.TypeToken
import net.corda.core.serialization.SerializableCalculatedProperty
import net.corda.serialization.internal.AllWhitelist
import net.corda.serialization.internal.amqp.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.jupiter.api.fail
import java.lang.reflect.Type
import java.util.*

class LocalTypeModelTests {

    private val descriptorBasedSerializerRegistry = DefaultDescriptorBasedSerializerRegistry()
    private val customSerializerRegistry: CustomSerializerRegistry = CachingCustomSerializerRegistry(descriptorBasedSerializerRegistry)
    private val model = ConfigurableLocalTypeModel(WhitelistBasedTypeModelConfiguration(AllWhitelist, customSerializerRegistry))
    private val emptyCustomSerializerRegistry = object: CustomSerializerRegistry {
        override val customSerializerNames: List<String> = emptyList()
        override fun register(customSerializer: CustomSerializer<out Any>) {}
        override fun registerExternal(customSerializer: CorDappCustomSerializer) {}
        override fun findCustomSerializer(clazz: Class<*>, declaredType: Type): AMQPSerializer<Any>? = null
    }
    private val modelWithoutOpacity = ConfigurableLocalTypeModel(WhitelistBasedTypeModelConfiguration(AllWhitelist, emptyCustomSerializerRegistry))

    interface CollectionHolder<K, V> {
        val list: List<V>
        val map: Map<K, V>
        val array: Array<List<V>>
    }

    open class StringKeyedCollectionHolder<T>(override val list: List<T>, override val map: Map<String, T>, override val array: Array<List<T>>) : CollectionHolder<String, T>

    class StringCollectionHolder(list: List<String>, map: Map<String, String>, array: Array<List<String>>) : StringKeyedCollectionHolder<String>(list, map, array)

    @Suppress("unused")
    class Nested(
            val collectionHolder: StringKeyedCollectionHolder<out Int>?,
            private val intArray: IntArray,
            @Suppress("UNUSED_PARAMETER") optionalParam: Short?)

    // This can't be treated as a composable type, because the [intArray] parameter is mandatory but we have no readable
    // field or property to populate it from.
    @Suppress("unused")
    class NonComposableNested(val collectionHolder: StringKeyedCollectionHolder<out Int>?, @Suppress("UNUSED_PARAMETER") intArray: IntArray)

    @Test(timeout=300_000)
	fun `Primitives and collections`() {
        assertInformation<CollectionHolder<UUID, String>>("CollectionHolder<UUID, String>")

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
            Nested(collectionHolder: StringKeyedCollectionHolder<Integer>?, intArray: int[], optionalParam: Short?)
              collectionHolder (optional): StringKeyedCollectionHolder<Integer>(list: List<Integer>, map: Map<String, Integer>, array: List<Integer>[]): CollectionHolder<String, Integer>
                array: List<Integer>[]
                list: List<Integer>
                map: Map<String, Integer>
              intArray: int[]
        """)

        assertInformation<NonComposableNested>("NonComposableNested")
    }

    interface SuperSuper<A, B> {
        val a: A
        val b: B
    }

    interface Super<C> : SuperSuper<C, Double> {
        val c: List<C>
    }

    abstract class Abstract<T>(override val a: Array<T>, override val b: Double) : Super<Array<T>>

    class Concrete(a: Array<Int>, b: Double, override val c: List<Array<Int>>, val d: Int) : Abstract<Int>(a, b)

    @Test(timeout=300_000)
	fun `interfaces and superclasses`() {
        assertInformation<SuperSuper<Int, Int>>("SuperSuper<Integer, Integer>")
        assertInformation<Super<UUID>>("Super<UUID>: SuperSuper<UUID, Double>")
        assertInformation<Abstract<String>>("""
            Abstract<String>: Super<String[]>, SuperSuper<String[], Double>
              a: String[]
              b: Double
        """)
        assertInformation<Concrete>("""
            Concrete(a: Integer[], b: double, c: List<Integer[]>, d: int): Abstract<Integer>, Super<Integer[]>, SuperSuper<Integer[], Double>
              a: Integer[]
              b: Double
              c: List<Integer[]>
              d: int
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

    @Test(timeout=300_000)
	fun `getter setter and calculated properties`() {
        assertInformation<OldStylePojoImpl>("""
           OldStylePojoImpl(): OldStylePojo<int[]>
             a (optional): int[]
             b: String
             c (calculated): String
        """)
    }

    class AliasingOldStylePojoImpl(override var a: String?, override var b: String, override val c: String): OldStylePojo<String>

    @Test(timeout=300_000)
	fun `calculated properties aliased by fields in implementing classes`() {
        assertInformation<AliasingOldStylePojoImpl>("""
           AliasingOldStylePojoImpl(a: String?, b: String, c: String): OldStylePojo<String>
             a (optional): String
             b: String
             c: String
        """)
    }

    class TransitivelyNonComposable(
        val a: String,
        val b: Exception,
        val c: MissingConstructorParameter,
        val d: AnotherTransitivelyNonComposable
    )

    class AnotherTransitivelyNonComposable(val e: String, val f: Exception, val g: OneMoreTransitivelyNonComposable)
    class OneMoreTransitivelyNonComposable(val h: String, val i: Exception)
    class MissingConstructorParameter(val a: String, @Suppress("UNUSED_PARAMETER") b: Exception)

    @Test(timeout=300_000)
	fun `no unique deserialization constructor creates non-composable type`() {
        modelWithoutOpacity.inspect(typeOf<Exception>()).let { typeInformation ->
            assertTrue(typeInformation is LocalTypeInformation.NonComposable)
            typeInformation as LocalTypeInformation.NonComposable
            assertEquals(
                "No unique deserialization constructor can be identified",
                typeInformation.reason
            )
            assertEquals(
                "Either annotate a constructor for this type with @ConstructorForDeserialization, or provide a custom serializer for it",
                typeInformation.remedy
            )
        }
    }

    @Test(timeout=300_000)
	fun `missing constructor parameters creates non-composable type`() {
        modelWithoutOpacity.inspect(typeOf<MissingConstructorParameter>()).let { typeInformation ->
            assertTrue(typeInformation is LocalTypeInformation.NonComposable)
            typeInformation as LocalTypeInformation.NonComposable
            assertEquals(
                "Mandatory constructor parameters [b] are missing from the readable properties [a]",
                typeInformation.reason
            )
            assertEquals(
                "Either provide getters or readable fields for [b], or provide a custom serializer for this type",
                typeInformation.remedy
            )
        }
    }

    @Test(timeout=300_000)
	fun `transitive types are non-composable creates non-composable type`() {
        modelWithoutOpacity.inspect(typeOf<TransitivelyNonComposable>()).let { typeInformation ->
            assertTrue(typeInformation is LocalTypeInformation.NonComposable)
            typeInformation as LocalTypeInformation.NonComposable
            assertEquals(
                """
                Has properties [b, c, d] of types that are not serializable:
                b [${Exception::class.java}]: No unique deserialization constructor can be identified
                c [${MissingConstructorParameter::class.java}]: Mandatory constructor parameters [b] are missing from the readable properties [a]
                d [${AnotherTransitivelyNonComposable::class.java}]: Has properties [f, g] of types that are not serializable:
                    f [${Exception::class.java}]: No unique deserialization constructor can be identified
                    g [${OneMoreTransitivelyNonComposable::class.java}]: Has properties [i] of types that are not serializable:
                        i [${Exception::class.java}]: No unique deserialization constructor can be identified
                """.trimIndent(), typeInformation.reason
            )
            assertEquals(
                "Either ensure that the properties [b, c, d] are serializable, or provide a custom serializer for this type",
                typeInformation.remedy
            )
        }
    }

    @Suppress("unused")
    enum class CustomEnum {
        ONE,
        TWO;

        override fun toString(): String {
            return "[${name.toLowerCase()}]"
        }
    }

    @Test(timeout = 300_000)
    fun `test type information for customised enum`() {
        modelWithoutOpacity.inspect(typeOf<CustomEnum>()).let { typeInformation ->
            val anEnum = typeInformation as? LocalTypeInformation.AnEnum ?: fail("Not AnEnum!")
            assertThat(anEnum.members).containsExactlyElementsOf(
                CustomEnum::class.java.enumConstants.map(CustomEnum::name)
            )
        }
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