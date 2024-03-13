package net.corda.serialization.internal.verifier

import net.corda.core.contracts.Attachment
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.Party
import net.corda.core.internal.SerializedTransactionState
import net.corda.core.node.NetworkParameters
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.transactions.CoreTransaction
import net.corda.core.utilities.Try
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.security.PublicKey

typealias SerializedNetworkParameters = SerializedBytes<NetworkParameters>

@CordaSerializable
sealed class ExternalVerifierInbound {
    data class Initialisation(
            val customSerializerClassNames: Set<String>,
            val serializationWhitelistClassNames: Set<String>,
            val customSerializationSchemeClassName: String?,
            val serializedCurrentNetworkParameters: SerializedNetworkParameters
    ) : ExternalVerifierInbound() {
        val currentNetworkParameters: NetworkParameters by lazy { serializedCurrentNetworkParameters.deserialize() }

        override fun toString(): String {
            return "Initialisation(" +
                    "customSerializerClassNames=$customSerializerClassNames, " +
                    "serializationWhitelistClassNames=$serializationWhitelistClassNames, " +
                    "customSerializationSchemeClassName=$customSerializationSchemeClassName, " +
                    "currentNetworkParameters=$currentNetworkParameters)"
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

fun DataOutputStream.writeCordaSerializable(payload: Any) {
    val serialised = payload.serialize()
    writeInt(serialised.size)
    serialised.writeTo(this)
    flush()
}

inline fun <reified T : Any> DataInputStream.readCordaSerializable(): T {
    val length = readInt()
    val bytes = readNBytes(length)
    if (bytes.size != length) {
        throw EOFException("Incomplete read of ${T::class.java.name}")
    }
    return bytes.deserialize<T>()
}
