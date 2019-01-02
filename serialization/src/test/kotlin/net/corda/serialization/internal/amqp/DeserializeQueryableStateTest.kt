package net.corda.serialization.internal.amqp

import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.serialization.SerializedBytes
import net.corda.serialization.internal.AMQP_RPC_CLIENT_CONTEXT
import net.corda.serialization.internal.AllWhitelist
import net.corda.serialization.internal.amqp.testutils.*
import net.corda.serialization.internal.carpenter.ClassCarpenterImpl
import org.junit.Test

data class TestState(override val participants: List<AbstractParty>): QueryableState {
    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        throw NotImplementedError()
    }
    override fun supportedSchemas(): Iterable<MappedSchema> {
        throw NotImplementedError()
    }
}

/**
 * A class loader that excludes [TestState].
 */
class ClassLoaderWithoutTestState(classLoader: ClassLoader) : ClassLoader(classLoader) {
    override fun loadClass(name: String?, resolve: Boolean): Class<*> {
        if (name != null && name.contains("TestState")) {
            throw ClassNotFoundException()
        }
        return super.loadClass(name, resolve)
    }
}

class DeserializeQueryableStateTest {

    /**
     * https://r3-cev.atlassian.net/browse/CORDA-2330
     */
    @Test
    fun `should deserialize subclass of QueryableState that is not present in the class loader`() {
        val instance = TestState(listOf())
        val serialized = TestSerializationOutput(false, testDefaultFactory()).serialize(instance)
        val context = AMQP_RPC_CLIENT_CONTEXT.copy(whitelist = AllWhitelist, deserializationClassLoader = ClassLoaderWithoutTestState(ClassLoader.getSystemClassLoader()))
        val sf = SerializerFactoryBuilder.build(context.whitelist, context.deserializationClassLoader, context.lenientCarpenterEnabled)
        val bytes = SerializedBytes<QueryableState>(serialized.bytes)
        // doesn't throw
        DeserializationInput(sf).deserialize(bytes, context)
    }
}