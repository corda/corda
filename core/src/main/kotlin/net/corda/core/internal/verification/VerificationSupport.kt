package net.corda.core.internal.verification

import net.corda.core.contracts.Attachment
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.internal.SerializedTransactionState
import net.corda.core.node.NetworkParameters
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.internal.AttachmentsClassLoaderCache
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.defaultVerifier
import java.security.PublicKey

/**
 * Represents the operations required to resolve and verify a transaction.
 */
interface VerificationSupport {
    val isInProcess: Boolean get() = true

    val appClassLoader: ClassLoader

    val attachmentsClassLoaderCache: AttachmentsClassLoaderCache? get() = null

    // TODO Use SequencedCollection if upgraded to Java 21
    fun getParties(keys: Collection<PublicKey>): List<Party?>

    fun getAttachment(id: SecureHash): Attachment?

    // TODO Use SequencedCollection if upgraded to Java 21
    fun getAttachments(ids: Collection<SecureHash>): List<Attachment?> = ids.map(::getAttachment)

    fun isAttachmentTrusted(attachment: Attachment): Boolean

    fun getTrustedClassAttachment(className: String): Attachment?

    fun getNetworkParameters(id: SecureHash?): NetworkParameters?

    fun getSerializedState(stateRef: StateRef): SerializedTransactionState

    fun getStateAndRef(stateRef: StateRef): StateAndRef<*> = StateAndRef(getSerializedState(stateRef).deserialize(), stateRef)

    fun fixupAttachmentIds(attachmentIds: Collection<SecureHash>): Set<SecureHash>

    fun createVerifier(ltx: LedgerTransaction, serializationContext: SerializationContext): Verifier {
        return defaultVerifier(ltx, serializationContext)
    }
}
