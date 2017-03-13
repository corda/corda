package net.corda.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.Party
import net.corda.core.node.services.TimestampChecker
import net.corda.core.node.services.UniquenessProvider
import net.corda.core.transactions.FilteredTransaction
import net.corda.core.utilities.unwrap

class NonValidatingNotaryFlow(otherSide: Party,
                              timestampChecker: TimestampChecker,
                              uniquenessProvider: UniquenessProvider) : NotaryFlow.Service(otherSide, timestampChecker, uniquenessProvider) {
    /**
     * The received transaction is not checked for contract-validity, as that would require fully
     * resolving it into a [TransactionForVerification], for which the caller would have to reveal the whole transaction
     * history chain.
     * As a result, the Notary _will commit invalid transactions_ as well, but as it also records the identity of
     * the caller, it is possible to raise a dispute and verify the validity of the transaction and subsequently
     * undo the commit of the input states (the exact mechanism still needs to be worked out).
     */
    @Suspendable
    override fun receiveAndVerifyTx(): TransactionParts {
        val ftx = receive<FilteredTransaction>(otherSide).unwrap {
            it.verify()
            it
        }
        return TransactionParts(ftx.rootHash, ftx.filteredLeaves.inputs, ftx.filteredLeaves.timestamp)
    }
}