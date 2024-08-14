package net.corda.verifier

import net.corda.core.contracts.Attachment
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.internal.SerializedTransactionState
import net.corda.core.internal.verification.VerificationSupport
import net.corda.core.node.NetworkParameters
import net.corda.core.serialization.internal.AttachmentsClassLoaderCache
import java.security.PublicKey

class ExternalVerificationContext(
        override val appClassLoader: ClassLoader,
        override val attachmentsClassLoaderCache: AttachmentsClassLoaderCache,
        private val externalVerifier: ExternalVerifier,
        private val transactionInputsAndReferences: Map<StateRef, SerializedTransactionState>
) : VerificationSupport {
    override val isInProcess: Boolean get() = false

    override fun getParties(keys: Collection<PublicKey>): List<Party?> = externalVerifier.getParties(keys)

    override fun getAttachment(id: SecureHash): Attachment? = externalVerifier.getAttachment(id)?.attachment

    override fun getAttachments(ids: Collection<SecureHash>): List<Attachment?> {
        return externalVerifier.getAttachments(ids).map { it?.attachment }
    }

    override fun isAttachmentTrusted(attachment: Attachment): Boolean = externalVerifier.getAttachment(attachment.id)!!.isTrusted

    override fun getTrustedClassAttachments(className: String): List<Attachment> {
        return externalVerifier.getTrustedClassAttachments(className)
    }

    override fun getNetworkParameters(id: SecureHash?): NetworkParameters? = externalVerifier.getNetworkParameters(id)

    override fun getSerializedState(stateRef: StateRef): SerializedTransactionState = transactionInputsAndReferences.getValue(stateRef)

    override fun fixupAttachmentIds(attachmentIds: Collection<SecureHash>): Set<SecureHash> {
        return externalVerifier.fixupAttachmentIds(attachmentIds)
    }
}
