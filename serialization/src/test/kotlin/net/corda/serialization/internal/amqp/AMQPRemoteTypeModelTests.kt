package net.corda.serialization.internal.amqp

import com.google.common.reflect.TypeToken
import net.corda.serialization.internal.AllWhitelist
import net.corda.serialization.internal.amqp.testutils.serializeAndReturnSchema
import net.corda.serialization.internal.carpenter.ClassCarpenterImpl
import net.corda.serialization.internal.model.*
import net.corda.testing.core.SerializationEnvironmentRule
import org.junit.Rule
import org.junit.Test
import java.util.*

class AMQPRemoteTypeModelTests {

    @Rule
    @JvmField
    val serializationEnvRule = SerializationEnvironmentRule()

    private val factory = SerializerFactoryBuilder.build(AllWhitelist, ClassLoader.getSystemClassLoader())
    private val typeModel = AMQPRemoteTypeModel()

    private fun getRemoteType(obj: Any): RemoteTypeInformation {
        val output = SerializationOutput(factory)
        val schema = output.serializeAndReturnSchema(obj).schema
        println(schema)
        println(typeModel.interpret(schema).keys)
        return typeModel.interpret(schema).values.find { it.typeIdentifier.name == obj::class.java.name }!!
    }

    private inline fun <reified T: Any> typeOf() = object : TypeToken<T>() {}.type

    interface Interface<P, Q, R> {
        val array: Array<out P>
        val list: List<Q>
        val map: Map<Q, R>
    }

    enum class Enum : Interface<String, IntArray, Int> {
        FOO, BAR, BAZ;

        override val array: Array<out String> get() = emptyArray()
        override val list: List<IntArray> get() = emptyList()
        override val map: Map<IntArray, Int> get() = emptyMap()
    }

    open class Superclass<K, V>(override val array: Array<out String>, override val list: List<K>, override val map: Map<K, V>)
        : Interface<String, K, V>

    class C<V>(array: Array<out String>, list: List<UUID>, map: Map<UUID, V>, val enum: Enum): Superclass<UUID, V>(array, list, map)

    @Test
    fun primitives() {
        val singleValue = C(
                    arrayOf("a", "b"),
                    listOf(UUID.randomUUID()),
                    mapOf(UUID.randomUUID() to intArrayOf(1, 2, 3)),
                Enum.BAZ)
        val remoteType = getRemoteType(singleValue)
        println(remoteType.prettyPrint())
    }
}