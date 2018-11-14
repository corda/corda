package net.corda.finance.compat

import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.transactions.SignedTransaction
import net.corda.finance.contracts.asset.Cash
import net.corda.serialization.internal.AllWhitelist
import net.corda.serialization.internal.amqp.DeserializationInput
import net.corda.serialization.internal.amqp.Schema
import net.corda.serialization.internal.amqp.SerializationOutput
import net.corda.serialization.internal.amqp.SerializerFactoryBuilder
import net.corda.serialization.internal.amqp.custom.PublicKeySerializer
import net.corda.testing.core.SerializationEnvironmentRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

// TODO: If this type of testing gets momentum, we can create a mini-framework that rides through list of files
// and performs necessary validation on all of them.
class CompatibilityTest {

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    val serializerFactory = SerializerFactoryBuilder.build(AllWhitelist, ClassLoader.getSystemClassLoader()).apply {
        register(PublicKeySerializer)
    }

    @Test
    fun issueCashTansactionReadTest() {
        val inputStream = javaClass.classLoader.getResourceAsStream("compatibilityData/v3/node_transaction.dat")
        assertNotNull(inputStream)

        val inByteArray: ByteArray = inputStream.readBytes()
        val input = DeserializationInput(serializerFactory)

        val (transaction, envelope) = input.deserializeAndReturnEnvelope(
                SerializedBytes(inByteArray),
                SignedTransaction::class.java,
                SerializationDefaults.STORAGE_CONTEXT)
        assertNotNull(transaction)

        val commands = transaction.tx.commands
        assertEquals(1, commands.size)
        assertTrue(commands.first().value is Cash.Commands.Issue)

        // Serialize back and check that representation is byte-to-byte identical to what it was originally.
        val output = SerializationOutput(serializerFactory)
        val (serializedBytes, schema) = output.serializeAndReturnSchema(transaction, SerializationDefaults.STORAGE_CONTEXT)

        assertSchemasMatch(envelope.schema, schema)

        assertTrue(inByteArray.contentEquals(serializedBytes.bytes))
    }

    private fun assertSchemasMatch(original: Schema, reserialized: Schema) {
        if (original.toString() != reserialized.toString()) {
            original.types.forEach { envelopeType ->
                val schemaType = reserialized.types.firstOrNull { it.name == envelopeType.name } ?:
                fail(
                        """Schema mismatch between original and re-serialized data. Could not find a schema matching:

|$envelopeType
""")

                if (schemaType.toString() != envelopeType.toString())
                    fail("""
Schema mismatch between original and re-serialized data. Expected:

$envelopeType

but was:

$schemaType
""")
            }
        }
    }
}