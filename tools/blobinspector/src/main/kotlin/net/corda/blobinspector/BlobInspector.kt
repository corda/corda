package net.corda.blobinspector

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.google.common.io.BaseEncoding
import com.jcabi.manifests.Manifests
import net.corda.client.jackson.JacksonSupport
import net.corda.core.internal.isRegularFile
import net.corda.core.internal.rootMessage
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.internal.SerializationEnvironmentImpl
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.utilities.sequence
import net.corda.serialization.internal.AMQP_P2P_CONTEXT
import net.corda.serialization.internal.AMQP_STORAGE_CONTEXT
import net.corda.serialization.internal.CordaSerializationMagic
import net.corda.serialization.internal.SerializationFactoryImpl
import net.corda.serialization.internal.amqp.AbstractAMQPSerializationScheme
import net.corda.serialization.internal.amqp.DeserializationInput
import net.corda.serialization.internal.amqp.amqpMagic
import picocli.CommandLine
import picocli.CommandLine.*
import java.io.PrintStream
import java.net.MalformedURLException
import java.net.URL
import java.nio.file.Paths
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val main = BlobInspector()
    try {
        CommandLine.run(main, *args)
    } catch (e: ExecutionException) {
        val throwable = e.cause ?: e
        if (main.verbose) {
            throwable.printStackTrace()
        } else {
            System.err.println("*ERROR*: ${throwable.rootMessage}. Use --verbose for more details")
        }
        exitProcess(1)
    }
}

@Command(
        name = "blob-inspector",
        versionProvider = CordaVersionProvider::class,
        mixinStandardHelpOptions = true,   // add --help and --version options,
        showDefaultValues = true,
        description = ["Convert AMQP serialised binary blobs to text"]
)
class BlobInspector : Runnable {
    @Parameters(index = "0", paramLabel = "SOURCE", description = ["URL or file path to the blob"], converter = [SourceConverter::class])
    private var source: URL? = null

    @Option(names = ["--format"], paramLabel = "type", description = ["Output format. Possible values: [YAML, JSON]"])
    private var formatType: OutputFormatType = OutputFormatType.YAML

    @Option(names = ["--input-format"], paramLabel = "type", description = ["Input format. If the file can't be decoded with the given value it's auto-detected, so you should never normally need to specify this. Possible values: [BINARY, HEX, BASE64]"])
    private var inputFormatType: InputFormatType = InputFormatType.BINARY

    @Option(names = ["--full-parties"],
            description = ["Display the owningKey and certPath properties of Party and PartyAndReference objects respectively"])
    private var fullParties: Boolean = false

    @Option(names = ["--schema"], description = ["Print the blob's schema first"])
    private var schema: Boolean = false

    @Option(names = ["--verbose"], description = ["Enable verbose output"])
    var verbose: Boolean = false

    override fun run() = run(System.out)

    fun run(out: PrintStream) {
        if (verbose) {
            System.setProperty("logLevel", "trace")
        }

        val inputBytes = source!!.readBytes()
        val bytes = parseToBinaryRelaxed(inputFormatType, inputBytes)
                ?: throw IllegalArgumentException("Error: this input does not appear to be encoded in Corda's AMQP extended format, sorry.")

        if (schema) {
            val envelope = DeserializationInput.getEnvelope(bytes.sequence())
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

        initialiseSerialization()
        try {
            val deserialized = bytes.deserialize<Any>(context = SerializationDefaults.STORAGE_CONTEXT)
            out.println(deserialized.javaClass.name)
            mapper.writeValue(out, deserialized)
        } finally {
            _contextSerializationEnv.set(null)
        }
    }

    private fun parseToBinaryRelaxed(format: InputFormatType, inputBytes: ByteArray): ByteArray? {
        // Try the format the user gave us first, then try the others.
        return parseToBinary(format, inputBytes) ?: parseToBinary(InputFormatType.HEX, inputBytes)
        ?: parseToBinary(InputFormatType.BASE64, inputBytes) ?: parseToBinary(InputFormatType.BINARY, inputBytes)
    }

    private fun parseToBinary(format: InputFormatType, inputBytes: ByteArray): ByteArray? {
        try {
            val bytes = when (format) {
                InputFormatType.BINARY -> inputBytes
                InputFormatType.HEX -> BaseEncoding.base16().decode(String(inputBytes).trim().toUpperCase())
                InputFormatType.BASE64 -> BaseEncoding.base64().decode(String(inputBytes).trim())
            }
            require(bytes.size > amqpMagic.size) { "Insufficient bytes for AMQP blob" }
            return if (bytes.copyOf(amqpMagic.size).contentEquals(amqpMagic.bytes)) {
                if (verbose)
                    println("Parsing input as $format")
                bytes
            } else {
                null   // Not an AMQP blob.
            }
        } catch (t: Throwable) {
            return null   // Failed to parse in some other way.
        }
    }

    private fun initialiseSerialization() {
        // Deserialise with the lenient carpenter as we only care for the AMQP field getters
        _contextSerializationEnv.set(SerializationEnvironmentImpl(
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

private class CordaVersionProvider : IVersionProvider {
    override fun getVersion(): Array<String> {
        return arrayOf(
                "Version: ${Manifests.read("Corda-Release-Version")}",
                "Revision: ${Manifests.read("Corda-Revision")}"
        )
    }
}

private enum class OutputFormatType { YAML, JSON }
private enum class InputFormatType { BINARY, HEX, BASE64 }

