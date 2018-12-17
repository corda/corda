package net.corda.serialization.internal.amqp

import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
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
class ClassLoaderWithoutTestState(classLoader: ClassLoader): ClassLoader(classLoader) {
    override fun loadClass(name: String?, resolve: Boolean): Class<*> {
        if(name != null && name.contains("TestState")) {
            throw ClassNotFoundException()
        }
        return super.loadClass(name, resolve)
    }
}

class DeserializeQueyableStateTest {

    /**
     * Reproduce CORDA-2330
     * https://r3-cev.atlassian.net/browse/CORDA-2330
     */
    @Test
    fun queryableState() {

        val sf = SerializerFactoryBuilder.build(AllWhitelist, ClassCarpenterImpl(AllWhitelist, ClassLoaderWithoutTestState(ClassLoader.getSystemClassLoader())))
        val instance = TestState(listOf())
        val serialized = TestSerializationOutput(false, testDefaultFactory()).serialize(instance)

        DeserializationInput(sf).deserialize(serialized)
    }
}