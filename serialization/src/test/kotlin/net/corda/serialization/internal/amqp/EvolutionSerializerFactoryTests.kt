package net.corda.serialization.internal.amqp

import net.corda.serialization.internal.amqp.testutils.deserializeAndReturnEnvelope
import net.corda.serialization.internal.amqp.testutils.serialize
import net.corda.serialization.internal.amqp.testutils.testDefaultFactory
import net.corda.serialization.internal.model.RemoteTypeInformation
import net.corda.serialization.internal.model.TypeIdentifier
import org.junit.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class EvolutionSerializerFactoryTests {

    private val factory = testDefaultFactory()

    @Test
    fun preservesDataWhenFlagSet() {
        val nonStrictEvolutionSerializerFactory = DefaultEvolutionSerializerFactory(
                factory,
                ClassLoader.getSystemClassLoader(),
                false)

        val strictEvolutionSerializerFactory = DefaultEvolutionSerializerFactory(
                factory,
                ClassLoader.getSystemClassLoader(),
                true)

        @Suppress("unused")
        class C(val importantFieldA: Int)
        val (_, env) = DeserializationInput(factory).deserializeAndReturnEnvelope(
                SerializationOutput(factory).serialize(C(1)))

        val remoteTypeInformation = AMQPRemoteTypeModel().interpret(SerializationSchemas(env.schema, env.transformsSchema))
                .values.find { it.typeIdentifier == TypeIdentifier.forClass(C::class.java) }
                as RemoteTypeInformation.Composable

        val withAddedField = remoteTypeInformation.copy(properties = remoteTypeInformation.properties.plus(
            "importantFieldB" to remoteTypeInformation.properties["importantFieldA"]!!))

        val localTypeInformation = factory.getTypeInformation(C::class.java)

        // No evolution required with original fields.
        assertNull(strictEvolutionSerializerFactory.getEvolutionSerializer(remoteTypeInformation, localTypeInformation))

        // Returns an evolution serializer if the fields have changed.
        assertNotNull(nonStrictEvolutionSerializerFactory.getEvolutionSerializer(withAddedField, localTypeInformation))

        // Fails in strict mode if the remote type information includes a field not included in the local type.
        assertFailsWith<EvolutionSerializationException> {
            strictEvolutionSerializerFactory.getEvolutionSerializer(withAddedField, localTypeInformation)
        }
    }

}