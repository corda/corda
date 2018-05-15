/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.UpgradedContract
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.internal.ContractUpgradeUtils
import net.corda.core.transactions.ContractUpgradeWireTransaction
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction

// TODO: We should have a whitelist of contracts we're willing to accept at all, and reject if the transaction
//       includes us in any outside that list. Potentially just if it includes any outside that list at all.
// TODO: Do we want to be able to reject specific transactions on more complex rules, for example reject incoming
//       cash without from unknown parties?
class FinalityHandler(private val sender: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        subFlow(ReceiveTransactionFlow(sender, true, StatesToRecord.ONLY_RELEVANT))
    }
}

class NotaryChangeHandler(otherSideSession: FlowSession) : AbstractStateReplacementFlow.Acceptor<Party>(otherSideSession) {
    /**
     * Check the notary change proposal.
     *
     * For example, if the proposed new notary has the same behaviour (e.g. both are non-validating)
     * and is also in a geographically convenient location we can just automatically approve the change.
     * TODO: In more difficult cases this should call for human attention to manually verify and approve the proposal
     */
    override fun verifyProposal(stx: SignedTransaction, proposal: AbstractStateReplacementFlow.Proposal<Party>) {
        val state = proposal.stateRef
        val proposedTx = stx.resolveNotaryChangeTransaction(serviceHub)
        val newNotary = proposal.modification

        if (state !in proposedTx.inputs.map { it.ref }) {
            throw StateReplacementException("The proposed state $state is not in the proposed transaction inputs")
        }

        // TODO: load and compare against notary whitelist from config. Remove the check below
        val isNotary = serviceHub.networkMapCache.isNotary(newNotary)
        if (!isNotary) {
            throw StateReplacementException("The proposed node $newNotary does not run a Notary service")
        }
    }
}

class ContractUpgradeHandler(otherSide: FlowSession) : AbstractStateReplacementFlow.Acceptor<Class<out UpgradedContract<ContractState, *>>>(otherSide) {
    @Suspendable
    override fun verifyProposal(stx: SignedTransaction, proposal: AbstractStateReplacementFlow.Proposal<Class<out UpgradedContract<ContractState, *>>>) {
        // Retrieve signed transaction from our side, we will apply the upgrade logic to the transaction on our side, and
        // verify outputs matches the proposed upgrade.
        val ourSTX = serviceHub.validatedTransactions.getTransaction(proposal.stateRef.txhash)
        requireNotNull(ourSTX) { "We don't have a copy of the referenced state" }
        val oldStateAndRef = ourSTX!!.tx.outRef<ContractState>(proposal.stateRef.index)
        val authorisedUpgrade = serviceHub.contractUpgradeService.getAuthorisedContractUpgrade(oldStateAndRef.ref) ?: throw IllegalStateException("Contract state upgrade is unauthorised. State hash : ${oldStateAndRef.ref}")
        val proposedTx = stx.coreTransaction as ContractUpgradeWireTransaction
        val expectedTx = ContractUpgradeUtils.assembleUpgradeTx(oldStateAndRef, proposal.modification, proposedTx.privacySalt, serviceHub)
        requireThat {
            "The instigator is one of the participants" using (initiatingSession.counterparty in oldStateAndRef.state.data.participants)
            "The proposed upgrade ${proposal.modification.javaClass} is a trusted upgrade path" using (proposal.modification.name == authorisedUpgrade)
            "The proposed tx matches the expected tx for this upgrade" using (proposedTx == expectedTx)
        }
        proposedTx.resolve(serviceHub, stx.sigs)
    }
}
