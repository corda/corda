package net.corda.core.internal.notary

import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.crypto.*
import net.corda.core.flows.NotarisationRequestSignature
import net.corda.core.flows.NotaryError
import net.corda.core.identity.Party
import net.corda.core.utilities.contextLogger
import org.slf4j.Logger

/**
 * A base notary service implementation that provides functionality for cases where a signature by a single member
 * of the cluster is sufficient for transaction notarisation. For example, a single-node or a Raft notary.
 */
abstract class TrustedAuthorityNotaryService : NotaryService() {
    companion object {
        private val staticLog = contextLogger()
    }

    protected open val log: Logger get() = staticLog
    protected abstract val uniquenessProvider: UniquenessProvider

    /**
     * A NotaryException is thrown if any of the states have been consumed by a different transaction. Note that
     * this method does not throw an exception when input states are present multiple times within the transaction.
     */
    @JvmOverloads
    fun commitInputStates(
            inputs: List<StateRef>,
            txId: SecureHash,
            caller: Party,
            requestSignature: NotarisationRequestSignature,
            timeWindow: TimeWindow?,
            references: List<StateRef> = emptyList()
    ) {
        try {
            uniquenessProvider.commit(inputs, txId, caller, requestSignature, timeWindow, references)
        } catch (e: NotaryInternalException) {
            if (e.error is NotaryError.Conflict) {
                val allInputs = inputs + references
                val conflicts = allInputs.filterIndexed { _, stateRef ->
                    val cause = e.error.consumedStates[stateRef]
                    cause != null && cause.hashOfTransactionId != txId.sha256()
                }
                if (conflicts.isNotEmpty()) {
                    // TODO: Create a new UniquenessException that only contains the conflicts filtered above.
                    log.info("Notary conflicts for $txId: $conflicts")
                    throw e
                }
            } else throw e
        } catch (e: Exception) {
            if (e is NotaryInternalException) throw  e
            log.error("Internal error", e)
            throw NotaryInternalException(NotaryError.General(Exception("Service unavailable, please try again later")))
        }
    }

    /** Sign a [ByteArray] input. */
    fun sign(bits: ByteArray): DigitalSignature.WithKey {
        return services.keyManagementService.sign(bits, notaryIdentityKey)
    }

    /** Sign a single transaction. */
    fun sign(txId: SecureHash): TransactionSignature {
        val signableData = SignableData(txId, SignatureMetadata(services.myInfo.platformVersion, Crypto.findSignatureScheme(notaryIdentityKey).schemeNumberID))
        return services.keyManagementService.sign(signableData, notaryIdentityKey)
    }

    // TODO: Sign multiple transactions at once by building their Merkle tree and then signing over its root.
}
