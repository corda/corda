package net.corda.serialization.internal.amqp

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

    private val descriptorBasedSerializerRegistry = DefaultDescriptorBasedSerializerRegistry()
    private val customSerializerRegistry: CustomSerializerRegistry = CachingCustomSerializerRegistry(descriptorBasedSerializerRegistry)
    private val localTypeModel = ConfigurableLocalTypeModel(WhitelistBasedTypeModelConfiguration(AllWhitelist, customSerializerRegistry))
    private val localTypeFingerPrinter = CustomisableLocalTypeInformationFingerPrinter(
            factory,
            localTypeModel)

    private val reflector = TypeLoadingRemoteTypeReflector(
            ClassCarpentingTypeLoader(ClassCarpenterImpl(AllWhitelist), ClassLoader.getSystemClassLoader()),
            DefaultFingerprintingLocalTypeModel(localTypeModel, localTypeFingerPrinter))

    private fun <T: Any> getRemoteType(obj: T): RemoteTypeInformation {
        val output = SerializationOutput(factory)
        val schema = output.serializeAndReturnSchema(obj).schema
        println(schema)
        return typeModel.interpret(schema.types[0].descriptor.name!!.toString(), schema)
    }

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
        println("===")
        val reflectedType = reflector.reflect(remoteType)
        println(reflectedType.localTypeInformation.prettyPrint())
    }
}