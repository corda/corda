package net.corda.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker

/**
 * Initiates a flow that produces an Issue/Move or Exit Cash transaction.
 */
abstract class AbstractCashFlow<T>(override val progressTracker: ProgressTracker) : FlowLogic<T>() {
    companion object {
        object GENERATING_ID : ProgressTracker.Step("Generating anonymous identities")
        object GENERATING_TX : ProgressTracker.Step("Generating transaction")
        object SIGNING_TX : ProgressTracker.Step("Signing transaction")
        object FINALISING_TX : ProgressTracker.Step("Finalising transaction")

        fun tracker() = ProgressTracker(GENERATING_ID, GENERATING_TX, SIGNING_TX, FINALISING_TX)
    }

    @Suspendable
    protected fun finaliseTx(participants: Set<Party>, tx: SignedTransaction, message: String) {
        try {
            subFlow(FinalityFlow(tx, participants))
        } catch (e: NotaryException) {
            throw CashException(message, e)
        }
    }

    /**
     * Combined signed transaction and identity lookup map, which is the resulting data from regular cash flows.
     * Specialised flows for unit tests differ from this.
     *
     * @param stx the signed transaction.
     * @param identities a mapping from the original identities of the parties to the anonymised equivalents.
     */
    @CordaSerializable
    data class Result(val stx: SignedTransaction, val identities: LinkedHashMap<Party, AnonymisedIdentity>?)
}

class CashException(message: String, cause: Throwable) : FlowException(message, cause)