package net.corda.finance.flows

import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.SerializationFactory
import net.corda.core.serialization.SerializedBytes
import net.corda.core.transactions.ComponentGroup
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.OpaqueBytes
import net.corda.finance.contracts.asset.Cash
import net.corda.serialization.internal.AllWhitelist
import net.corda.serialization.internal.CordaSerializationEncoding
import net.corda.serialization.internal.amqp.DeserializationInput
import net.corda.serialization.internal.amqp.Schema
import net.corda.serialization.internal.amqp.SerializationOutput
import net.corda.serialization.internal.amqp.SerializerFactory
import net.corda.serialization.internal.amqp.SerializerFactoryBuilder
import net.corda.serialization.internal.amqp.custom.BigDecimalSerializer
import net.corda.serialization.internal.amqp.custom.CurrencySerializer
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

    val serializerFactory: SerializerFactory = SerializerFactoryBuilder.build(AllWhitelist, ClassLoader.getSystemClassLoader()).apply {
        register(PublicKeySerializer)
        register(BigDecimalSerializer)
        register(CurrencySerializer)
    }

    @Test(timeout=300_000)
	fun issueCashTansactionReadTest() {
        val inputStream = javaClass.classLoader.getResourceAsStream("compatibilityData/v3/node_transaction.dat")
        assertNotNull(inputStream)

        val inByteArray: ByteArray = inputStream.readBytes()
        println("Original size = ${inByteArray.size}")
        val input = DeserializationInput(serializerFactory)

        val (transaction, envelope) = input.deserializeAndReturnEnvelope(
                SerializedBytes(inByteArray),
                SignedTransaction::class.java,
                SerializationDefaults.STORAGE_CONTEXT)
        assertNotNull(transaction)

        val commands = transaction.tx.commands
        assertEquals(1, commands.size)
        assertTrue(commands.first().value is Cash.Commands.Issue)

        val newWtx = SerializationFactory.defaultFactory.asCurrent {
            withCurrentContext(SerializationDefaults.STORAGE_CONTEXT) {
                WireTransaction(transaction.tx.componentGroups.map { cg: ComponentGroup ->
                    ComponentGroup(cg.groupIndex, cg.components.map { bytes ->
                        val componentInput = DeserializationInput(serializerFactory)
                        val component = componentInput.deserialize(SerializedBytes<Any>(bytes.bytes), SerializationDefaults.STORAGE_CONTEXT)
                        val componentOutput = SerializationOutput(serializerFactory)
                        val componentOutputBytes = componentOutput.serialize(component, SerializationDefaults.STORAGE_CONTEXT.withIntegerFingerprint()).bytes
                        OpaqueBytes(componentOutputBytes)
                    })
                })
            }
        }
        val newTransaction = SignedTransaction(newWtx, transaction.sigs)

        // Serialize back and check that representation is byte-to-byte identical to what it was originally.
        val output = SerializationOutput(serializerFactory)
        val outByteArray = output.serialize(newTransaction, SerializationDefaults.STORAGE_CONTEXT.withEncoding(CordaSerializationEncoding.SNAPPY)
                .withIntegerFingerprint()).bytes
        //val (serializedBytes, schema) = output.serializeAndReturnSchema(transaction, SerializationDefaults.STORAGE_CONTEXT)
        println("Output size = ${outByteArray.size}")

        //assertSchemasMatch(envelope.schema, schema)

        //assertTrue(inByteArray.contentEquals(serializedBytes.bytes))
    }

    private fun assertSchemasMatch(original: Schema, reserialized: Schema) {
        if (original.toString() == reserialized.toString()) return
        original.types.forEach { originalType ->
            val reserializedType = reserialized.types.firstOrNull { it.name == originalType.name } ?:
            fail("""Schema mismatch between original and re-serialized data. Could not find reserialized schema matching:

$originalType
""")

                if (originalType.toString() != reserializedType.toString())
                    fail("""Schema mismatch between original and re-serialized data. Expected:

$originalType

but was:

$reserializedType
""")
        }

        reserialized.types.forEach { reserializedType ->
            if (original.types.none { it.name == reserializedType.name })
            fail("""Schema mismatch between original and re-serialized data. Could not find original schema matching:

$reserializedType
""")
        }
    }
}