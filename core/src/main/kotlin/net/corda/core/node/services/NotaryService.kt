package net.corda.core.node.services

import com.google.common.primitives.Booleans
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.crypto.*
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.serialize
import net.corda.core.utilities.contextLogger
import org.slf4j.Logger
import java.security.PublicKey
import java.time.Clock

abstract class NotaryService : SingletonSerializeAsToken() {
    companion object {
        @Deprecated("No longer used")
        const val ID_PREFIX = "corda.notary."
        @Deprecated("No longer used")
        fun constructId(validating: Boolean, raft: Boolean = false, bft: Boolean = false, custom: Boolean = false): String {
            require(Booleans.countTrue(raft, bft, custom) <= 1) { "At most one of raft, bft or custom may be true" }
            return StringBuffer(ID_PREFIX).apply {
                append(if (validating) "validating" else "simple")
                if (raft) append(".raft")
                if (bft) append(".bft")
                if (custom) append(".custom")
            }.toString()
        }

        /**
         * Checks if the current instant provided by the clock falls within the specified time window.
         *
         * @throws NotaryException if current time is outside the specified time window. The exception contains
         *                         the [NotaryError.TimeWindowInvalid] error.
         */
        @JvmStatic
        @Throws(NotaryException::class)
        fun validateTimeWindow(clock: Clock, timeWindow: TimeWindow?) {
            if (timeWindow == null) return
            val currentTime = clock.instant()
            if (currentTime !in timeWindow) {
                throw NotaryException(
                        NotaryError.TimeWindowInvalid(currentTime, timeWindow)
                )
            }
        }
    }

    abstract val services: ServiceHub
    abstract val notaryIdentityKey: PublicKey

    abstract fun start()
    abstract fun stop()

    /**
     * Produces a notary service flow which has the corresponding sends and receives as [NotaryFlow.Client].
     * @param otherPartySession client [Party] making the request
     */
    abstract fun createServiceFlow(otherPartySession: FlowSession): FlowLogic<Void?>
}

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

    fun validateTimeWindow(t: TimeWindow?) = NotaryService.validateTimeWindow(services.clock, t)

    /**
     * A NotaryException is thrown if any of the states have been consumed by a different transaction. Note that
     * this method does not throw an exception when input states are present multiple times within the transaction.
     */
    fun commitInputStates(inputs: List<StateRef>, txId: SecureHash, caller: Party) {
        try {
            uniquenessProvider.commit(inputs, txId, caller)
        } catch (e: UniquenessException) {
            val conflicts = inputs.filterIndexed { i, stateRef ->
                val consumingTx = e.error.stateHistory[stateRef]
                consumingTx != null && consumingTx != UniquenessProvider.ConsumingTx(txId, i, caller)
            }
            if (conflicts.isNotEmpty()) {
                // TODO: Create a new UniquenessException that only contains the conflicts filtered above.
                log.warn("Notary conflicts for $txId: $conflicts")
                throw notaryException(txId, e)
            }
        } catch (e: Exception) {
            log.error("Internal error", e)
            throw NotaryException(NotaryError.General("Service unavailable, please try again later"))
        }
    }

    private fun notaryException(txId: SecureHash, e: UniquenessException): NotaryException {
        val conflictData = e.error.serialize()
        val signedConflict = SignedData(conflictData, sign(conflictData.bytes))
        return NotaryException(NotaryError.Conflict(txId, signedConflict))
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

    @Deprecated("This property is no longer used") @Suppress("DEPRECATION")
    protected open val timeWindowChecker: TimeWindowChecker get() = throw UnsupportedOperationException("No default implementation, need to override")
}