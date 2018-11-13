package net.corda.node.services.transactions

import net.corda.core.contracts.ComponentGroupEnum
import net.corda.core.flows.FlowSession
import net.corda.core.flows.NotarisationPayload
import net.corda.core.internal.notary.NotaryServiceFlow
import net.corda.core.internal.notary.SinglePartyNotaryService
import net.corda.core.transactions.ContractUpgradeFilteredTransaction
import net.corda.core.transactions.FilteredTransaction
import net.corda.core.transactions.NotaryChangeWireTransaction

/**
 * The received transaction is not checked for contract-validity, as that would require fully
 * resolving it into a [TransactionForVerification], for which the caller would have to reveal the whole transaction
 * history chain.
 * As a result, the Notary _will commit invalid transactions_ as well, but as it also records the identity of
 * the caller, it is possible to raise a dispute and verify the validity of the transaction and subsequently
 * undo the commit of the input states (the exact mechanism still needs to be worked out).
 */
class NonValidatingNotaryFlow(otherSideSession: FlowSession, service: SinglePartyNotaryService) : NotaryServiceFlow(otherSideSession, service) {
    override fun extractParts(requestPayload: NotarisationPayload): TransactionParts {
        val tx = requestPayload.coreTransaction
        return when (tx) {
            is FilteredTransaction -> {
                tx.apply {
                    verify()
                    checkAllComponentsVisible(ComponentGroupEnum.INPUTS_GROUP)
                    checkAllComponentsVisible(ComponentGroupEnum.TIMEWINDOW_GROUP)
                    checkAllComponentsVisible(ComponentGroupEnum.REFERENCES_GROUP)
                    if(serviceHub.networkParameters.minimumPlatformVersion >= 4) checkAllComponentsVisible(ComponentGroupEnum.PARAMETERS_GROUP)
                }
                TransactionParts(tx.id, tx.inputs, tx.timeWindow, tx.notary, tx.references, networkParametersHash = tx.networkParametersHash)
            }
            is ContractUpgradeFilteredTransaction,
            is NotaryChangeWireTransaction -> TransactionParts(tx.id, tx.inputs, null, tx.notary, networkParametersHash = tx.networkParametersHash)
            else -> {
                throw IllegalArgumentException("Received unexpected transaction type: ${tx::class.java.simpleName}," +
                        "expected either ${FilteredTransaction::class.java.simpleName} or ${NotaryChangeWireTransaction::class.java.simpleName}")
            }
        }
    }
}