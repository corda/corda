package net.corda.serialization.internal.amqp

import net.corda.client.rpc.internal.serialization.amqp.AMQPClientSerializationScheme
import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.serialization.SerializedBytes
import net.corda.node.serialization.amqp.AMQPServerSerializationScheme
import net.corda.serialization.internal.AMQP_RPC_CLIENT_CONTEXT
import net.corda.serialization.internal.AMQP_RPC_SERVER_CONTEXT
import net.corda.serialization.internal.AllWhitelist
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.TestIdentity
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

data class TestState(override val participants: List<AbstractParty>): QueryableState {
    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        throw NotImplementedError()
    }
    override fun supportedSchemas(): Iterable<MappedSchema> {
        // return dummy value
        return listOf(MappedSchema(TestState::class.java, 432, listOf()))
    }
}


/**
 * A class loader that excludes [TestState].
 */
class ClassLoaderWithoutTestState(classLoader: ClassLoader) : ClassLoader(classLoader) {
    override fun loadClass(name: String?, resolve: Boolean): Class<*> {
        if (name != null && name == TestState::class.java.name) {
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
        val testParty = TestIdentity(DUMMY_BANK_A_NAME).identity.party
        val instance = TestState(listOf(testParty))

        val scheme = AMQPServerSerializationScheme(emptyList())
        val serialized = scheme.serialize(instance, AMQP_RPC_SERVER_CONTEXT)

        val clientContext = AMQP_RPC_CLIENT_CONTEXT.copy(whitelist = AllWhitelist, deserializationClassLoader = ClassLoaderWithoutTestState(ClassLoader.getSystemClassLoader()))
        val serializedBytes = SerializedBytes<QueryableState>(serialized.bytes)
        val clientScheme = AMQPClientSerializationScheme(emptyList())

        // this operation used to fail because AMQP_RPC_CLIENT_CONTEXT.lenientCarpenterEnabled was false
        val deserialized = clientScheme.deserialize(serializedBytes, QueryableState::class.java, clientContext)
        assertFailsWith<AbstractMethodError> { deserialized.supportedSchemas() }
        assertEquals(deserialized.participants.first(), testParty)
    }
}