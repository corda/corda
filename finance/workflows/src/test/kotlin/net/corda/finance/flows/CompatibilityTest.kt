package net.corda.finance.flows

import com.github.luben.zstd.ZstdDictTrainer
import net.corda.core.serialization.ExternalSchema
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
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

enum class SchemaType {
    REPEAT,
    SINGLE,
    NONE;
}

// TODO: If this type of testing gets momentum, we can create a mini-framework that rides through list of files
// and performs necessary validation on all of them.
@RunWith(Parameterized::class)
class CompatibilityTest(val encoding: CordaSerializationEncoding?, val schemaType: SchemaType, val useDictionary: Boolean, val useIntegerFingerprints: Boolean) {

    companion object {
        @Parameterized.Parameters(name = "encoding: {0}, schemaType: {1}, useDictionary: {2}, useIntegerFingerprints: {3}")
        @JvmStatic
        fun data(): List<Array<Any?>> = listOf(
                arrayOf<Any?>(null, SchemaType.REPEAT, false, false),
                arrayOf<Any?>(null, SchemaType.SINGLE, false, false),
                arrayOf<Any?>(null, SchemaType.NONE, false, false),
                arrayOf<Any?>(null, SchemaType.REPEAT, false, true),
                arrayOf<Any?>(null, SchemaType.SINGLE, false, true),
                arrayOf<Any?>(null, SchemaType.NONE, false, true),
                arrayOf<Any?>(CordaSerializationEncoding.DEFLATE, SchemaType.REPEAT, false, false),
                arrayOf<Any?>(CordaSerializationEncoding.DEFLATE, SchemaType.SINGLE, false, false),
                arrayOf<Any?>(CordaSerializationEncoding.DEFLATE, SchemaType.NONE, false, false),
                arrayOf<Any?>(CordaSerializationEncoding.DEFLATE, SchemaType.REPEAT, false, true),
                arrayOf<Any?>(CordaSerializationEncoding.DEFLATE, SchemaType.SINGLE, false, true),
                arrayOf<Any?>(CordaSerializationEncoding.DEFLATE, SchemaType.NONE, false, true),
                arrayOf<Any?>(CordaSerializationEncoding.SNAPPY, SchemaType.REPEAT, false, false),
                arrayOf<Any?>(CordaSerializationEncoding.SNAPPY, SchemaType.SINGLE, false, false),
                arrayOf<Any?>(CordaSerializationEncoding.SNAPPY, SchemaType.NONE, false, false),
                arrayOf<Any?>(CordaSerializationEncoding.SNAPPY, SchemaType.REPEAT, false, true),
                arrayOf<Any?>(CordaSerializationEncoding.SNAPPY, SchemaType.SINGLE, false, true),
                arrayOf<Any?>(CordaSerializationEncoding.SNAPPY, SchemaType.NONE, false, true),
                arrayOf<Any?>(CordaSerializationEncoding.ZSTANDARD, SchemaType.REPEAT, false, false),
                arrayOf<Any?>(CordaSerializationEncoding.ZSTANDARD, SchemaType.SINGLE, false, false),
                arrayOf<Any?>(CordaSerializationEncoding.ZSTANDARD, SchemaType.NONE, false, false),
                arrayOf<Any?>(CordaSerializationEncoding.ZSTANDARD, SchemaType.REPEAT, true, false),
                arrayOf<Any?>(CordaSerializationEncoding.ZSTANDARD, SchemaType.SINGLE, true, false),
                arrayOf<Any?>(CordaSerializationEncoding.ZSTANDARD, SchemaType.NONE, true, false),
                arrayOf<Any?>(CordaSerializationEncoding.ZSTANDARD, SchemaType.REPEAT, true, true),
                arrayOf<Any?>(CordaSerializationEncoding.ZSTANDARD, SchemaType.SINGLE, true, true),
                arrayOf<Any?>(CordaSerializationEncoding.ZSTANDARD, SchemaType.NONE, true, true)
        )
    }

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
        //println("Original size = ${inByteArray.size}")
        val input = DeserializationInput(serializerFactory)

        val (transaction, envelope) = input.deserializeAndReturnEnvelope(
                SerializedBytes(inByteArray),
                SignedTransaction::class.java,
                SerializationDefaults.STORAGE_CONTEXT)
        assertNotNull(transaction)

        val commands = transaction.tx.commands
        assertEquals(1, commands.size)
        assertTrue(commands.first().value is Cash.Commands.Issue)

        val networkParams = javaClass.classLoader.getResourceAsStream("networkParams.r3corda.6a6b6f256").readBytes()
        val trainer = ZstdDictTrainer(128 * 1024, 128 * 1024)
        while (useDictionary) {
            if (!trainer.addSample(inByteArray)) break
            if (!trainer.addSample(networkParams)) break
        }
        val dict = if (useDictionary) trainer.trainSamples() else ByteArray(0)
        val context = SerializationDefaults.STORAGE_CONTEXT.let {
            if (schemaType != SchemaType.REPEAT) {
                it.withExternalSchema(ExternalSchema())
            } else {
                it
            }
        }.let {
            if (useIntegerFingerprints) {
                it.withIntegerFingerprint()
            } else {
                it
            }
        }
        //.withExternalSchema(ExternalSchema()).withIntegerFingerprint()
        val newWtx = SerializationFactory.defaultFactory.asCurrent {
            withCurrentContext(context) {
                WireTransaction(transaction.tx.componentGroups.map { cg: ComponentGroup ->
                    ComponentGroup(cg.groupIndex, cg.components.map { bytes ->
                        val componentInput = DeserializationInput(serializerFactory)
                        val component = componentInput.deserialize(SerializedBytes<Any>(bytes.bytes), SerializationDefaults.STORAGE_CONTEXT)
                        val componentOutput = SerializationOutput(serializerFactory)
                        val componentOutputBytes = componentOutput.serialize(component, context).bytes
                        OpaqueBytes(componentOutputBytes)
                    })
                })
            }
        }
        val newTransaction = SignedTransaction(newWtx, transaction.sigs)

        // Serialize back and check that representation is byte-to-byte identical to what it was originally.
        val output = SerializationOutput(serializerFactory)
        val outerContext = context.let {
            if (encoding != null) {
                it.withEncoding(encoding)
            } else it
        }.let {
            if (schemaType != SchemaType.REPEAT) {
                it.withExternalSchema(context.externalSchema!!.copy(flush = schemaType == SchemaType.SINGLE))
            } else {
                it
            }
        }.let {
            if (useDictionary) {
                it.withProperty(CordaSerializationEncoding.DICTIONARY_KEY, dict)
            } else {
                it
            }
        }
        val outByteArray = output.serialize(newTransaction, outerContext /*context.withExternalSchema(context.externalSchema!!.copy(flush = true))
                .withEncoding(CordaSerializationEncoding.ZSTANDARD).withProperty(CordaSerializationEncoding.DICTIONARY_KEY, dict)*/).bytes
        //val (serializedBytes, schema) = output.serializeAndReturnSchema(transaction, SerializationDefaults.STORAGE_CONTEXT)
        println("encoding: $encoding, schemaType: $schemaType, useDictionary: $useDictionary, useIntegerFingerprints: $useIntegerFingerprints, Output size = ${outByteArray.size}")

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