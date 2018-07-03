package net.corda.blobinspector

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.jcabi.manifests.Manifests
import net.corda.client.jackson.JacksonSupport
import net.corda.core.internal.isRegularFile
import net.corda.core.internal.rootMessage
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.internal.SerializationEnvironmentImpl
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.utilities.ByteSequence
import net.corda.core.utilities.sequence
import net.corda.nodeapi.internal.serialization.AMQP_P2P_CONTEXT
import net.corda.nodeapi.internal.serialization.SerializationFactoryImpl
import net.corda.nodeapi.internal.serialization.amqp.AbstractAMQPSerializationScheme
import net.corda.nodeapi.internal.serialization.amqp.AmqpHeaderV1_0
import net.corda.nodeapi.internal.serialization.amqp.DeserializationInput
import picocli.CommandLine
import picocli.CommandLine.*
import java.net.MalformedURLException
import java.net.URL
import java.nio.file.Paths
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val main = Main()
    try {
        CommandLine.run(main, *args)
    } catch (e: ExecutionException) {
        val throwable = e.cause ?: e
        if (main.verbose) {
            throwable.printStackTrace()
        } else {
            System.err.println("*ERROR*: ${throwable.rootMessage ?: "Use --verbose for more details"}")
        }
        exitProcess(1)
    }
}

@Command(
        name = "Blob Inspector",
        versionProvider = CordaVersionProvider::class,
        mixinStandardHelpOptions = true, // add --help and --version options,
        showDefaultValues = true,
        description = arrayOf("Inspect AMQP serialised binary blobs")
)
class Main : Runnable {
    @Parameters(index = "0", paramLabel = "SOURCE", description = arrayOf("URL or file path to the blob"), converter = arrayOf(SourceConverter::class))
    private var source: URL? = null

    @Option(names = arrayOf("--format"), paramLabel = "type", description = arrayOf("Output format. Possible values: [YAML, JSON]"))
    private var formatType: FormatType = FormatType.YAML

    @Option(names = arrayOf("--full-parties"),
            description = arrayOf("Display the owningKey and certPath properties of Party and PartyAndReference objects respectively"))
    private var fullParties: Boolean = false

    @Option(names = arrayOf("--schema"), description = arrayOf("Print the blob's schema first"))
    private var schema: Boolean = false

    @Option(names = arrayOf("--verbose"), description = arrayOf("Enable verbose output"))
    var verbose: Boolean = false

    override fun run() {
        if (verbose) {
            System.setProperty("logLevel", "trace")
        }

        val bytes = source!!.readBytes().run {
            require(size > AmqpHeaderV1_0.size) { "Insufficient bytes for AMQP blob" }
            sequence()
        }

        require(bytes.take(AmqpHeaderV1_0.size) == AmqpHeaderV1_0) { "Not an AMQP blob" }

        if (schema) {
            val envelope = DeserializationInput.getEnvelope(bytes)
            println(envelope.schema)
            println()
            println(envelope.transformsSchema)
            println()
        }

        initialiseSerialization()

        val factory = when (formatType) {
            FormatType.YAML -> YAMLFactory()
            FormatType.JSON -> JsonFactory()
        }
        val mapper = JacksonSupport.createNonRpcMapper(factory, fullParties)

        val deserialized = bytes.deserialize<Any>()
        println(deserialized.javaClass.name)
        mapper.writeValue(System.out, deserialized)
    }

    private fun initialiseSerialization() {
        _contextSerializationEnv.set(SerializationEnvironmentImpl(
                SerializationFactoryImpl().apply {
                    registerScheme(AMQPInspectorSerializationScheme)
                },
                AMQP_P2P_CONTEXT
        ))
    }
}

private object AMQPInspectorSerializationScheme : AbstractAMQPSerializationScheme(emptyList()) {
    override fun canDeserializeVersion(byteSequence: ByteSequence, target: SerializationContext.UseCase): Boolean {
        return byteSequence == AmqpHeaderV1_0 && target == SerializationContext.UseCase.P2P
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

private enum class FormatType { YAML, JSON }

