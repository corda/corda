package net.corda.blobinspector

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import net.corda.client.jackson.JacksonSupport
import net.corda.cliutils.CordaCliWrapper
import net.corda.cliutils.ExitCodes
import net.corda.cliutils.start
import net.corda.core.internal.isRegularFile
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.internal.SerializationEnvironment
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.utilities.base64ToByteArray
import net.corda.core.utilities.hexToByteArray
import net.corda.core.utilities.sequence
import net.corda.serialization.internal.AMQP_P2P_CONTEXT
import net.corda.serialization.internal.AMQP_STORAGE_CONTEXT
import net.corda.serialization.internal.CordaSerializationMagic
import net.corda.serialization.internal.SerializationFactoryImpl
import net.corda.serialization.internal.amqp.AbstractAMQPSerializationScheme
import net.corda.serialization.internal.amqp.DeserializationInput
import net.corda.serialization.internal.amqp.amqpMagic
import picocli.CommandLine.*
import java.io.PrintStream
import java.net.MalformedURLException
import java.net.URL
import java.nio.file.Paths

fun main(args: Array<String>) {
    BlobInspector().start(args)
}

class BlobInspector : CordaCliWrapper("blob-inspector", "Convert AMQP serialised binary blobs to text") {
    @Parameters(index = "0", paramLabel = "SOURCE", description = ["URL or file path to the blob"], converter = [SourceConverter::class])
    var source: URL? = null

    @Option(names = ["--format"], paramLabel = "type", description = ["Output format. Possible values: [YAML, JSON]"])
    private var formatType: OutputFormatType = OutputFormatType.YAML

    @Option(names = ["--input-format"], paramLabel = "type", description = ["Input format. If the file can't be decoded with the given value it's auto-detected, so you should never normally need to specify this. Possible values: [BINARY, HEX, BASE64]"])
    private var inputFormatType: InputFormatType = InputFormatType.BINARY

    @Option(names = ["--full-parties"],
            description = ["Display the owningKey and certPath properties of Party and PartyAndReference objects respectively"])
    private var fullParties: Boolean = false

    @Option(names = ["--schema"], description = ["Prints the blob's schema first"])
    private var schema: Boolean = false

    override fun runProgram() = run(System.out)

    fun run(out: PrintStream): Int {
        val inputBytes = source!!.readBytes()
        val bytes = parseToBinaryRelaxed(inputFormatType, inputBytes)
                ?: throw IllegalArgumentException("Error: this input does not appear to be encoded in Corda's AMQP extended format, sorry.")

        initialiseSerialization()

        if (schema) {
            val envelope = DeserializationInput.getEnvelope(bytes.sequence(), SerializationDefaults.STORAGE_CONTEXT.encodingWhitelist)
            out.println(envelope.schema)
            out.println()
            out.println(envelope.transformsSchema)
            out.println()
        }

        val factory = when (formatType) {
            OutputFormatType.YAML -> YAMLFactory()
            OutputFormatType.JSON -> JsonFactory()
        }

        val mapper = JacksonSupport.createNonRpcMapper(factory, fullParties)

        return try {
            val deserialized = bytes.deserialize<Any>(context = SerializationDefaults.STORAGE_CONTEXT)
            out.println(deserialized.javaClass.name)
            mapper.writeValue(out, deserialized)
            ExitCodes.SUCCESS
        } catch (e: Exception) {
            ExitCodes.FAILURE
        } finally {
            _contextSerializationEnv.set(null)
        }
    }

    private fun parseToBinaryRelaxed(format: InputFormatType, inputBytes: ByteArray): ByteArray? {
        // Try the format the user gave us first, then try the others.
        //@formatter:off
        return parseToBinary(format, inputBytes) ?:
               parseToBinary(InputFormatType.HEX, inputBytes) ?:
               parseToBinary(InputFormatType.BASE64, inputBytes) ?:
               parseToBinary(InputFormatType.BINARY, inputBytes)
        //@formatter:on
    }

    private fun parseToBinary(format: InputFormatType, inputBytes: ByteArray): ByteArray? {
        try {
            val bytes = when (format) {
                InputFormatType.BINARY -> inputBytes
                InputFormatType.HEX -> String(inputBytes).trim().hexToByteArray()
                InputFormatType.BASE64 -> String(inputBytes).trim().base64ToByteArray()
            }
            require(bytes.size > amqpMagic.size) { "Insufficient bytes for AMQP blob" }
            return if (bytes.copyOf(amqpMagic.size).contentEquals(amqpMagic.bytes)) {
                if (verbose)
                    println("Parsing input as $format")
                bytes
            } else {
                null   // Not an AMQP blob.
            }
        } catch (e: Exception) {
            return null   // Failed to parse in some other way.
        }
    }

    private fun initialiseSerialization() {
        // Deserialise with the lenient carpenter as we only care for the AMQP field getters
        _contextSerializationEnv.set(SerializationEnvironment.with(
                SerializationFactoryImpl().apply {
                    registerScheme(AMQPInspectorSerializationScheme)
                },
                p2pContext = AMQP_P2P_CONTEXT.withLenientCarpenter(),
                storageContext = AMQP_STORAGE_CONTEXT.withLenientCarpenter()
        ))
    }
}

private object AMQPInspectorSerializationScheme : AbstractAMQPSerializationScheme(emptyList()) {
    override fun canDeserializeVersion(magic: CordaSerializationMagic, target: SerializationContext.UseCase): Boolean {
        return magic == amqpMagic
    }

    override fun rpcClientSerializerFactory(context: SerializationContext) = throw UnsupportedOperationException()
    override fun rpcServerSerializerFactory(context: SerializationContext) = throw UnsupportedOperationException()
}

private class SourceConverter : ITypeConverter<URL> {
    override fun convert(value: String): URL {
        return try {
            URL(value)
        } catch (e: MalformedURLException) {
            val path = Paths.get(value)
            require(path.isRegularFile()) { "$path is not a file" }
            path.toUri().toURL()
        }
    }
}

private enum class OutputFormatType { YAML, JSON }
private enum class InputFormatType { BINARY, HEX, BASE64 }
