package net.corda.node.services

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.TransactionType
import net.corda.core.contracts.UpgradedContract
import net.corda.core.contracts.requireThat
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.unwrap
import net.corda.flows.*

/**
 * This class sets up network message handlers for requests from peers for data keyed by hash. It is a piece of simple
 * glue that sits between the network layer and the database layer.
 *
 * Note that in our data model, to be able to name a thing by hash automatically gives the power to request it. There
 * are no access control lists. If you want to keep some data private, then you must be careful who you give its name
 * to, and trust that they will not pass the name onwards. If someone suspects some data might exist but does not have
 * its name, then the 256-bit search space they'd have to cover makes it physically impossible to enumerate, and as
 * such the hash of a piece of data can be seen as a type of password allowing access to it.
 *
 * Additionally, because nodes do not store invalid transactions, requesting such a transaction will always yield null.
 */
class FetchTransactionsHandler(otherParty: Party) : FetchDataHandler<SignedTransaction>(otherParty) {
    override fun getData(id: SecureHash): SignedTransaction? {
        return serviceHub.validatedTransactions.getTransaction(id)
    }
}

// TODO: Use Artemis message streaming support here, called "large messages". This avoids the need to buffer.
class FetchAttachmentsHandler(otherParty: Party) : FetchDataHandler<ByteArray>(otherParty) {
    override fun getData(id: SecureHash): ByteArray? {
        return serviceHub.attachments.openAttachment(id)?.open()?.readBytes()
    }
}

abstract class FetchDataHandler<out T>(val otherParty: Party) : FlowLogic<Unit>() {
    @Suspendable
    @Throws(FetchDataFlow.HashNotFound::class)
    override fun call() {
        val request = receive<FetchDataFlow.Request>(otherParty).unwrap {
            if (it.hashes.isEmpty()) throw FlowException("Empty hash list")
            it
        }
        val response = request.hashes.map {
            getData(it) ?: throw FetchDataFlow.HashNotFound(it)
        }
        send(otherParty, response)
    }

    protected abstract fun getData(id: SecureHash): T?
}

// TODO: We should have a whitelist of contracts we're willing to accept at all, and reject if the transaction
//       includes us in any outside that list. Potentially just if it includes any outside that list at all.
// TODO: Do we want to be able to reject specific transactions on more complex rules, for example reject incoming
//       cash without from unknown parties?
class NotifyTransactionHandler(val otherParty: Party) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val request = receive<BroadcastTransactionFlow.NotifyTxRequest>(otherParty).unwrap { it }
        subFlow(ResolveTransactionsFlow(request.tx, otherParty))
        serviceHub.recordTransactions(request.tx)
    }
}

class NotaryChangeHandler(otherSide: Party) : AbstractStateReplacementFlow.Acceptor<Party>(otherSide) {
    /**
     * Check the notary change proposal.
     *
     * For example, if the proposed new notary has the same behaviour (e.g. both are non-validating)
     * and is also in a geographically convenient location we can just automatically approve the change.
     * TODO: In more difficult cases this should call for human attention to manually verify and approve the proposal
     */
    override fun verifyProposal(proposal: AbstractStateReplacementFlow.Proposal<Party>): Unit {
        val state = proposal.stateRef
        val proposedTx = proposal.stx.tx

        if (proposedTx.type !is TransactionType.NotaryChange) {
            throw StateReplacementException("The proposed transaction is not a notary change transaction.")
        }

        val newNotary = proposal.modification
        val isNotary = serviceHub.networkMapCache.notaryNodes.any { it.notaryIdentity == newNotary }
        if (!isNotary) {
            throw StateReplacementException("The proposed node $newNotary does not run a Notary service")
        }
        if (state !in proposedTx.inputs) {
            throw StateReplacementException("The proposed state $state is not in the proposed transaction inputs")
        }

//            // An example requirement
//            val blacklist = listOf("Evil Notary")
//            checkProposal(newNotary.name !in blacklist) {
//                "The proposed new notary $newNotary is not trusted by the party"
//            }
    }
}

class ContractUpgradeHandler(otherSide: Party) : AbstractStateReplacementFlow.Acceptor<Class<out UpgradedContract<ContractState, *>>>(otherSide) {
    @Suspendable
    @Throws(StateReplacementException::class)
    override fun verifyProposal(proposal: AbstractStateReplacementFlow.Proposal<Class<out UpgradedContract<ContractState, *>>>) {
        // Retrieve signed transaction from our side, we will apply the upgrade logic to the transaction on our side, and
        // verify outputs matches the proposed upgrade.
        val stx = subFlow(FetchTransactionsFlow(setOf(proposal.stateRef.txhash), otherSide)).fromDisk.singleOrNull()
        requireNotNull(stx) { "We don't have a copy of the referenced state" }
        val oldStateAndRef = stx!!.tx.outRef<ContractState>(proposal.stateRef.index)
        val authorisedUpgrade = serviceHub.vaultService.getAuthorisedContractUpgrade(oldStateAndRef.ref) ?:
                throw IllegalStateException("Contract state upgrade is unauthorised. State hash : ${oldStateAndRef.ref}")
        val proposedTx = proposal.stx.tx
        val expectedTx = ContractUpgradeFlow.assembleBareTx(oldStateAndRef, proposal.modification).toWireTransaction()
        requireThat {
            "The instigator is one of the participants" using (otherSide in oldStateAndRef.state.data.participants)
            "The proposed upgrade ${proposal.modification.javaClass} is a trusted upgrade path" using (proposal.modification == authorisedUpgrade)
            "The proposed tx matches the expected tx for this upgrade" using (proposedTx == expectedTx)
        }
        ContractUpgradeFlow.verify(oldStateAndRef.state.data, expectedTx.outRef<ContractState>(0).state.data, expectedTx.commands.single())
    }
}
