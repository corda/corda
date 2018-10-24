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

    private val factory = SerializerFactory(AllWhitelist, ClassLoader.getSystemClassLoader())
    private val typeModel = AMQPRemoteTypeModel()

    private val localTypeModel = ConfigurableLocalTypeModel(WhitelistBasedTypeModelConfiguration(AllWhitelist))

    private val reflector = TypeLoadingRemoteTypeReflector(
            ClassCarpentingTypeLoader(ClassCarpenterImpl(AllWhitelist)),
            localTypeModel,
            getTypeModellingFingerPrinter(factory))

    private fun <T: Any> getRemoteType(obj: T): RemoteTypeInformation {
        val output = SerializationOutput(factory)
        val schema = output.serializeAndReturnSchema(obj).schema
        println(schema)
        return typeModel.interpret(SerializerFactory.nameForType(obj::class.java), schema)
    }

    interface Interface<P, Q, R> {
        val array: Array<out P>
        val list: List<Q>
        val map: Map<Q, R>
    }

    open class Superclass<K, V>(override val array: Array<out String>, override val list: List<K>, override val map: Map<K, V>)
        : Interface<String, K, V>

    class C<V>(array: Array<out String>, list: List<UUID>, map: Map<UUID, V>): Superclass<UUID, V>(array, list, map)

    @Test
    fun primitives() {
        val singleValue = C(
                    arrayOf("a", "b"),
                    listOf(UUID.randomUUID()),
                    mapOf(UUID.randomUUID() to intArrayOf(1, 2, 3)))
        val remoteType = getRemoteType(singleValue)
        println(remoteType.prettyPrint())
        println("===")
        val reflectedType = reflector.reflect(remoteType)
        println(reflectedType.localTypeInformation.prettyPrint())
    }
}