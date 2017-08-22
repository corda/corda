package net.corda.node.services.transactions

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.NotaryFlow
import net.corda.core.flows.TransactionParts
import net.corda.core.identity.Party
import net.corda.core.node.services.TrustedAuthorityNotaryService
import net.corda.core.transactions.FilteredTransaction
import net.corda.core.transactions.NotaryChangeWireTransaction
import net.corda.core.utilities.unwrap

class NonValidatingNotaryFlow(otherSide: Party, service: TrustedAuthorityNotaryService) : NotaryFlow.Service(otherSide, service) {
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
        val parts = receive<Any>(otherSide).unwrap {
            when (it) {
                is FilteredTransaction -> {
                    it.verifyAndAllInputsVisible()
                    TransactionParts(it.rootHash, it.filteredLeaves.inputs, it.filteredLeaves.timeWindow)
                }
                is NotaryChangeWireTransaction -> TransactionParts(it.id, it.inputs, null)
                else -> {
                    throw IllegalArgumentException("Received unexpected transaction type: ${it::class.java.simpleName}," +
                            "expected either ${FilteredTransaction::class.java.simpleName} or ${NotaryChangeWireTransaction::class.java.simpleName}")
                }
            }
        }
        return parts
    }
}