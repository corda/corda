package net.corda.serialization.internal.verifier

import net.corda.core.contracts.Attachment
import net.corda.core.contracts.RotatedKeys
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.Party
import net.corda.core.internal.SerializedTransactionState
import net.corda.core.node.NetworkParameters
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializationFactory
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.transactions.CoreTransaction
import net.corda.core.utilities.Try
import net.corda.core.utilities.sequence
import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.security.PublicKey
import kotlin.math.min
import kotlin.reflect.KClass

typealias SerializedNetworkParameters = SerializedBytes<NetworkParameters>
typealias SerializedRotatedKeys = SerializedBytes<RotatedKeys>

@CordaSerializable
sealed class ExternalVerifierInbound {
    data class Initialisation(
            val customSerializerClassNames: Set<String>,
            val serializationWhitelistClassNames: Set<String>,
            val customSerializationSchemeClassName: String?,
            val serializedCurrentNetworkParameters: SerializedNetworkParameters,
            val serializedRotatedKeys: SerializedRotatedKeys
    ) : ExternalVerifierInbound() {
        val currentNetworkParameters: NetworkParameters by lazy { serializedCurrentNetworkParameters.deserialize() }
        val rotatedKeys: RotatedKeys by lazy { serializedRotatedKeys.deserialize() }

        override fun toString(): String {
            return "Initialisation(" +
                    "customSerializerClassNames=$customSerializerClassNames, " +
                    "serializationWhitelistClassNames=$serializationWhitelistClassNames, " +
                    "customSerializationSchemeClassName=$customSerializationSchemeClassName, " +
                    "currentNetworkParameters=$currentNetworkParameters, " +
                    "rotatedKeys=$rotatedKeys)"
        }
    }

    data class VerificationRequest(
            val ctx: CoreTransaction,
            val ctxInputsAndReferences: Map<StateRef, SerializedTransactionState>
    ) : ExternalVerifierInbound() {
        override fun toString(): String = "VerificationRequest(ctx=$ctx)"
    }

    data class PartiesResult(val parties: List<Party?>) : ExternalVerifierInbound()
    data class AttachmentResult(val attachment: AttachmentWithTrust?) : ExternalVerifierInbound()
    data class AttachmentsResult(val attachments: List<AttachmentWithTrust?>) : ExternalVerifierInbound()
    data class NetworkParametersResult(val networkParameters: NetworkParameters?) : ExternalVerifierInbound()
    data class TrustedClassAttachmentsResult(val ids: List<SecureHash>) : ExternalVerifierInbound()
}

@CordaSerializable
data class AttachmentWithTrust(val attachment: Attachment, val isTrusted: Boolean)

@CordaSerializable
sealed class ExternalVerifierOutbound {
    sealed class VerifierRequest : ExternalVerifierOutbound() {
        data class GetParties(val keys: Set<PublicKey>) : VerifierRequest() {
            override fun toString(): String = "GetParties(keys=${keys.map { it.toStringShort() }}})"
        }
        data class GetAttachment(val id: SecureHash) : VerifierRequest()
        data class GetAttachments(val ids: Set<SecureHash>) : VerifierRequest()
        data class GetNetworkParameters(val id: SecureHash) : VerifierRequest()
        data class GetTrustedClassAttachments(val className: String) : VerifierRequest()
    }

    data class VerificationResult(val result: Try<Unit>) : ExternalVerifierOutbound()
}

fun SocketChannel.writeCordaSerializable(payload: Any) {
    val serialised = payload.serialize()
    val buffer = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE)
    buffer.putInt(serialised.size)
    var writtenSoFar = 0
    while (writtenSoFar < serialised.size) {
        val length = min(buffer.remaining(), serialised.size - writtenSoFar)
        serialised.subSequence(writtenSoFar, length).putTo(buffer)
        buffer.flip()
        write(buffer)
        writtenSoFar += length
        buffer.clear()
    }
}

fun <T : Any> SocketChannel.readCordaSerializable(clazz: KClass<T>): T {
    val length = ByteBuffer.wrap(read(clazz, Integer.BYTES)).getInt()
    val bytes = read(clazz, length)
    return SerializationFactory.defaultFactory.deserialize(bytes.sequence(), clazz.java, SerializationFactory.defaultFactory.defaultContext)
}

private fun SocketChannel.read(clazz: KClass<*>, length: Int): ByteArray {
    val bytes = ByteArray(length)
    var readSoFar = 0
    while (readSoFar < bytes.size) {
        // Wrap a ByteBuffer around the byte array to read directly into it
        val n = read(ByteBuffer.wrap(bytes, readSoFar, bytes.size - readSoFar))
        if (n == -1) {
            throw EOFException("Incomplete read of ${clazz.java.name}")
        }
        readSoFar += n
    }
    return bytes
}
