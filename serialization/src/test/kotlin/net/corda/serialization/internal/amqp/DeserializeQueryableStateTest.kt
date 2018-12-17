package net.corda.serialization.internal.amqp

import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializedBytes
import net.corda.serialization.internal.AllWhitelist
import net.corda.serialization.internal.SerializationContextImpl
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
class ClassLoaderWithoutTestState(classLoader: ClassLoader): ClassLoader(classLoader) {
    override fun loadClass(name: String?, resolve: Boolean): Class<*> {
        if(name != null && name.contains("TestState")) {
            throw ClassNotFoundException()
        }
        return super.loadClass(name, resolve)
    }
}

class DeserializeQueryableStateTest {

    /**
     * Set `lenient` to false to reproduce CORDA-2330
     * https://r3-cev.atlassian.net/browse/CORDA-2330
     */
    @Test
    fun `should deserialize subclass of QueryableState that is not present in the class loader`() {

        val lenient = true
        val instance = TestState(listOf())
        val serialized = TestSerializationOutput(false, testDefaultFactory()).serialize(instance)

        val sf = SerializerFactoryBuilder.build(AllWhitelist, ClassCarpenterImpl(AllWhitelist, ClassLoaderWithoutTestState(ClassLoader.getSystemClassLoader()), lenient))

        val context = SerializationContextImpl(
                preferredSerializationVersion = amqpMagic,
                deserializationClassLoader = ClassLoaderWithoutTestState(ClassLoader.getSystemClassLoader()),
                whitelist = AllWhitelist,
                properties = serializationProperties,
                objectReferencesEnabled = false,
                useCase = SerializationContext.UseCase.Testing,
                encoding = null)
        val bytes = SerializedBytes<QueryableState>(serialized.bytes)
        // will throw when lenient is false
        DeserializationInput(sf).deserialize(bytes, context)
    }
}