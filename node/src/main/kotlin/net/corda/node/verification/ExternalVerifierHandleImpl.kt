package net.corda.node.verification

import net.corda.core.contracts.Attachment
import net.corda.core.crypto.random63BitValue
import net.corda.core.internal.AbstractAttachment
import net.corda.core.internal.copyTo
import net.corda.core.internal.level
import net.corda.core.internal.mapToSet
import net.corda.core.internal.readFully
import net.corda.core.internal.toSimpleString
import net.corda.core.internal.verification.ExternalVerifierHandle
import net.corda.core.internal.verification.NodeVerificationSupport
import net.corda.core.serialization.serialize
import net.corda.core.transactions.CoreTransaction
import net.corda.core.utilities.Try
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.serialization.internal.GeneratedAttachment
import net.corda.serialization.internal.amqp.AbstractAMQPSerializationScheme.Companion.customSerializers
import net.corda.serialization.internal.amqp.AbstractAMQPSerializationScheme.Companion.serializationWhitelists
import net.corda.serialization.internal.verifier.AttachmentWithTrust
import net.corda.serialization.internal.verifier.ExternalVerifierInbound.AttachmentResult
import net.corda.serialization.internal.verifier.ExternalVerifierInbound.AttachmentsResult
import net.corda.serialization.internal.verifier.ExternalVerifierInbound.Initialisation
import net.corda.serialization.internal.verifier.ExternalVerifierInbound.NetworkParametersResult
import net.corda.serialization.internal.verifier.ExternalVerifierInbound.PartiesResult
import net.corda.serialization.internal.verifier.ExternalVerifierInbound.TrustedClassAttachmentsResult
import net.corda.serialization.internal.verifier.ExternalVerifierInbound.VerificationRequest
import net.corda.serialization.internal.verifier.ExternalVerifierOutbound
import net.corda.serialization.internal.verifier.ExternalVerifierOutbound.VerificationResult
import net.corda.serialization.internal.verifier.ExternalVerifierOutbound.VerifierRequest
import net.corda.serialization.internal.verifier.ExternalVerifierOutbound.VerifierRequest.GetAttachment
import net.corda.serialization.internal.verifier.ExternalVerifierOutbound.VerifierRequest.GetAttachments
import net.corda.serialization.internal.verifier.ExternalVerifierOutbound.VerifierRequest.GetNetworkParameters
import net.corda.serialization.internal.verifier.ExternalVerifierOutbound.VerifierRequest.GetParties
import net.corda.serialization.internal.verifier.ExternalVerifierOutbound.VerifierRequest.GetTrustedClassAttachments
import net.corda.serialization.internal.verifier.readCordaSerializable
import net.corda.serialization.internal.verifier.writeCordaSerializable
import java.io.IOException
import java.lang.Character.MAX_RADIX
import java.lang.ProcessBuilder.Redirect
import java.lang.management.ManagementFactory
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermissions.fromString
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.div
import kotlin.io.path.fileAttributesViewOrNull
import kotlin.io.path.isExecutable
import kotlin.io.path.isWritable

/**
 * Handle to the node's external verifier. The verifier process is started lazily on the first verification request.
 */
class ExternalVerifierHandleImpl(
        private val verificationSupport: NodeVerificationSupport,
        private val baseDirectory: Path
) : ExternalVerifierHandle {
    companion object {
        private val log = contextLogger()

        private const val MAX_ATTEMPTS = 5

        private val verifierJar: Path = Files.createTempFile("corda-external-verifier", ".jar")
        init {
            // Extract the embedded verifier jar
            Companion::class.java.getResourceAsStream("external-verifier.jar")!!.use {
                it.copyTo(verifierJar, REPLACE_EXISTING)
            }
            log.debug { "Extracted external verifier jar to ${verifierJar.absolutePathString()}" }
            verifierJar.toFile().deleteOnExit()
        }
    }

    private lateinit var socketFile: Path
    private lateinit var serverChannel: ServerSocketChannel
    @Volatile
    private var connection: Connection? = null

    override fun verifyTransaction(ctx: CoreTransaction) {
        log.info("Verify ${ctx.toSimpleString()} externally")
        // By definition input states are unique, and so it makes sense to eagerly send them across with the transaction.
        // Reference states are not, but for now we'll send them anyway and assume they aren't used often. If this assumption is not
        // correct, and there's a benefit, then we can send them lazily.
        val ctxInputsAndReferences = (ctx.inputs + ctx.references).associateWith(verificationSupport::getSerializedState)
        val request = VerificationRequest(ctx, ctxInputsAndReferences)

        // To keep things simple the verifier only supports one verification request at a time.
        synchronized(this) {
            startServer()
            var attempt = 1
            while (true) {
                val result = try {
                    tryVerification(request)
                } catch (e: Exception) {
                    processError(attempt, e)
                    attempt += 1
                    continue
                }
                when (result) {
                    is Try.Success -> return
                    is Try.Failure -> throw result.exception
                }
            }
        }
    }

    private fun startServer() {
        if (::socketFile.isInitialized) return
        // Try to create the UNIX domain file in /tmp to keep the full path under the 100 char limit. If we don't have access to it then
        // fallback to the temp dir specified by the JVM and hope it's short enough.
        val tempDir = Path("/tmp").takeIf { it.isWritable() && it.isExecutable() } ?: Path(System.getProperty("java.io.tmpdir"))
        socketFile = tempDir / "corda-external-verifier-${random63BitValue().toString(MAX_RADIX)}.socket"
        serverChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
        log.debug { "Binding to UNIX domain file $socketFile" }
        serverChannel.bind(UnixDomainSocketAddress.of(socketFile), 1)
        // Lock down access to the file
        socketFile.fileAttributesViewOrNull<PosixFileAttributeView>()?.setPermissions(fromString("rwx------"))
        // Just in case...
        Runtime.getRuntime().addShutdownHook(Thread(::close))
    }

    private fun processError(attempt: Int, e: Exception) {
        if (attempt == MAX_ATTEMPTS) {
            throw IOException("Unable to verify with external verifier", e)
        } else {
            log.warn("Unable to verify with external verifier, trying again...", e)
        }
        try {
            connection?.close()
        } catch (e: Exception) {
            log.debug("Problem closing external verifier connection", e)
        }
        connection = null
    }

    private fun tryVerification(request: VerificationRequest): Try<Unit> {
        val connection = getConnection()
        connection.channel.writeCordaSerializable(request)
        // Send the verification request and then wait for any requests from verifier for more information. The last message will either
        // be a verification success or failure message.
        while (true) {
            val message = connection.channel.readCordaSerializable(ExternalVerifierOutbound::class)
            log.debug { "Received from external verifier: $message" }
            when (message) {
                // Process the information the verifier needs and then loop back and wait for more messages
                is VerifierRequest -> processVerifierRequest(message, connection)
                is VerificationResult -> return message.result
            }
        }
    }

    private fun getConnection(): Connection {
        return connection ?: Connection().also { connection = it }
    }

    private fun processVerifierRequest(request: VerifierRequest, connection: Connection) {
        val result = when (request) {
            is GetParties -> PartiesResult(verificationSupport.getParties(request.keys))
            is GetAttachment -> AttachmentResult(verificationSupport.getAttachment(request.id)?.withTrust())
            is GetAttachments -> AttachmentsResult(verificationSupport.getAttachments(request.ids).map { it?.withTrust() })
            is GetNetworkParameters -> NetworkParametersResult(verificationSupport.getNetworkParameters(request.id))
            is GetTrustedClassAttachments -> TrustedClassAttachmentsResult(verificationSupport.getTrustedClassAttachments(request.className).map { it.id })
        }
        log.debug { "Sending response to external verifier: $result" }
        connection.channel.writeCordaSerializable(result)
    }

    private fun Attachment.withTrust(): AttachmentWithTrust {
        val isTrusted = verificationSupport.isAttachmentTrusted(this)
        val attachmentForSer = when (this) {
            // The Attachment retrieved from the database is not serialisable, so we have to convert it into one
            is AbstractAttachment -> GeneratedAttachment(open().readFully(), uploader)
            // For everything else we keep as is, in particular preserving ContractAttachment
            else -> this
        }
        return AttachmentWithTrust(attachmentForSer, isTrusted)
    }

    override fun close() {
        connection?.close()
        connection = null
        if (::serverChannel.isInitialized) {
            serverChannel.close()
        }
        if (::socketFile.isInitialized) {
            socketFile.deleteIfExists()
        }
    }

    private inner class Connection : AutoCloseable {
        private val verifierProcess: Process
        val channel: SocketChannel

        init {
            val inheritedJvmArgs = ManagementFactory.getRuntimeMXBean().inputArguments.filter { "--add-opens" in it }
            val command = ArrayList<String>()
            command += "${Path(System.getProperty("java.home"), "bin", "java")}"
            command += inheritedJvmArgs
            command += listOf(
                    "-jar",
                    "$verifierJar",
                    socketFile.absolutePathString(),
                    log.level.name.lowercase()
            )
            log.debug { "External verifier command: $command" }
            val logsDirectory = (baseDirectory / "logs").createDirectories()
            verifierProcess = ProcessBuilder(command)
                    .redirectOutput(Redirect.appendTo((logsDirectory / "verifier-stdout.log").toFile()))
                    .redirectError(Redirect.appendTo((logsDirectory / "verifier-stderr.log").toFile()))
                    .directory(baseDirectory.toFile())
                    .start()
            log.info("External verifier process started; PID ${verifierProcess.pid()}")

            verifierProcess.onExit().whenComplete { _, _ ->
                if (connection != null) {
                    log.warn("The external verifier has unexpectedly terminated with error code ${verifierProcess.exitValue()}. " +
                            "Please check verifier logs for more details.")
                }
                // Allow a new process to be started on the next verification request
                connection = null
            }

            channel = serverChannel.accept()

            val cordapps = verificationSupport.cordappProvider.cordapps
            val initialisation = Initialisation(
                    customSerializerClassNames = cordapps.customSerializers.mapToSet { it.javaClass.name },
                    serializationWhitelistClassNames = cordapps.serializationWhitelists.mapToSet { it.javaClass.name },
                    System.getProperty("experimental.corda.customSerializationScheme"), // See Node#initialiseSerialization
                    serializedCurrentNetworkParameters = verificationSupport.networkParameters.serialize()
            )
            channel.writeCordaSerializable(initialisation)
        }

        override fun close() {
            try {
                channel.close()
            } finally {
                verifierProcess.destroyForcibly()
            }
        }
    }
}
