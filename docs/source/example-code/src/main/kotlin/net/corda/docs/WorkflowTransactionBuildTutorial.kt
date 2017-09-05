package net.corda.docs

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.*
import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria.VaultQueryCriteria
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.seconds
import net.corda.core.utilities.unwrap

// Minimal state model of a manual approval process
@CordaSerializable
enum class WorkflowState {
    NEW,
    APPROVED,
    REJECTED
}

/**
 * Minimal contract to encode a simple workflow with one initial state and two possible eventual states.
 * It is assumed one party unilaterally submits and the other manually retrieves the deal and completes it.
 */
data class TradeApprovalContract(private val blank: Void? = null) : Contract {

    interface Commands : CommandData {
        class Issue : TypeOnlyCommandData(), Commands  // Record receipt of deal details
        class Completed : TypeOnlyCommandData(), Commands  // Record match
    }

    /**
     * Truly minimal state that just records a tradeId string and the parties involved.
     */
    data class State(val tradeId: String,
                     val source: Party,
                     val counterparty: Party,
                     val state: WorkflowState = WorkflowState.NEW,
                     override val linearId: UniqueIdentifier = UniqueIdentifier(tradeId),
                     override val contract: TradeApprovalContract = TradeApprovalContract()) : LinearState {

        val parties: List<Party> get() = listOf(source, counterparty)
        override val participants: List<AbstractParty> get() = parties
    }

    /**
     * The verify method locks down the allowed transactions to contain just a single proposal being
     * created/modified and the only modification allowed is to the state field.
     */
    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<TradeApprovalContract.Commands>()
        requireNotNull(tx.timeWindow) { "must have a time-window" }
        when (command.value) {
            is Commands.Issue -> {
                requireThat {
                    "Issue of new WorkflowContract must not include any inputs" using (tx.inputs.isEmpty())
                    "Issue of new WorkflowContract must be in a unique transaction" using (tx.outputs.size == 1)
                }
                val issued = tx.outputsOfType<TradeApprovalContract.State>().single()
                requireThat {
                    "Issue requires the source Party as signer" using (command.signers.contains(issued.source.owningKey))
                    "Initial Issue state must be NEW" using (issued.state == WorkflowState.NEW)
                }
            }
            is Commands.Completed -> {
                val stateGroups = tx.groupStates(TradeApprovalContract.State::class.java) { it.linearId }
                require(stateGroups.size == 1) { "Must be only a single proposal in transaction" }
                for ((inputs, outputs) in stateGroups) {
                    val before = inputs.single()
                    val after = outputs.single()
                    requireThat {
                        "Only a non-final trade can be modified" using (before.state == WorkflowState.NEW)
                        "Output must be a final state" using (after.state in setOf(WorkflowState.APPROVED, WorkflowState.REJECTED))
                        "Completed command can only change state" using (before == after.copy(state = before.state))
                        "Completed command requires the source Party as signer" using (command.signers.contains(before.source.owningKey))
                        "Completed command requires the counterparty as signer" using (command.signers.contains(before.counterparty.owningKey))
                    }
                }
            }
            else -> throw IllegalArgumentException("Unrecognised Command $command")
        }
    }
}

/**
 * Simple flow to create a workflow state, sign and notarise it.
 * The protocol then sends a copy to the other node. We don't require the other party to sign
 * as their approval/rejection is to follow.
 */
class SubmitTradeApprovalFlow(val tradeId: String,
                              val counterparty: Party) : FlowLogic<StateAndRef<TradeApprovalContract.State>>() {
    @Suspendable
    override fun call(): StateAndRef<TradeApprovalContract.State> {
        // Manufacture an initial state
        val tradeProposal = TradeApprovalContract.State(
                tradeId,
                serviceHub.myInfo.legalIdentity,
                counterparty)
        // identify a notary. This might also be done external to the flow
        val notary = serviceHub.networkMapCache.getAnyNotary()
        // Create the TransactionBuilder and populate with the new state.
        val tx = TransactionBuilder(notary)
                .withItems(tradeProposal, Command(TradeApprovalContract.Commands.Issue(), listOf(tradeProposal.source.owningKey)))
        tx.setTimeWindow(serviceHub.clock.instant(), 60.seconds)
        // We can automatically sign as there is no untrusted data.
        val signedTx = serviceHub.signInitialTransaction(tx)
        // Notarise and distribute.
        subFlow(FinalityFlow(signedTx, setOf(serviceHub.myInfo.legalIdentity, counterparty)))
        // Return the initial state
        return signedTx.tx.outRef<TradeApprovalContract.State>(0)
    }

}

/**
 * Simple flow to complete a proposal submitted by another party and ensure both nodes
 * end up with a fully signed copy of the state either as APPROVED, or REJECTED
 */
@InitiatingFlow
class SubmitCompletionFlow(val ref: StateRef, val verdict: WorkflowState) : FlowLogic<StateAndRef<TradeApprovalContract.State>>() {
    init {
        require(verdict in setOf(WorkflowState.APPROVED, WorkflowState.REJECTED)) {
            "Verdict must be a final state"
        }
    }

    @Suspendable
    override fun call(): StateAndRef<TradeApprovalContract.State> {
        // DOCSTART 1
        val criteria = VaultQueryCriteria(stateRefs = listOf(ref))
        val latestRecord = serviceHub.vaultQueryService.queryBy<TradeApprovalContract.State>(criteria).states.single()
        // DOCEND 1

        // Check the protocol hasn't already been run
        require(latestRecord.ref == ref) {
            "Input trade $ref is not latest version $latestRecord"
        }
        // Require that the state is still modifiable
        require(latestRecord.state.data.state == WorkflowState.NEW) {
            "Input trade not modifiable ${latestRecord.state.data.state}"
        }
        // Check we are the correct Party to run the protocol. Note they will counter check this too.
        require(latestRecord.state.data.counterparty == serviceHub.myInfo.legalIdentity) {
            "The counterparty must give the verdict"
        }

        // DOCSTART 2
        // Modify the state field for new output. We use copy, to ensure no other modifications.
        // It is especially important for a LinearState that the linearId is copied across,
        // not accidentally assigned a new random id.
        val newState = latestRecord.state.data.copy(state = verdict)

        // We have to use the original notary for the new transaction
        val notary = latestRecord.state.notary

        // Get and populate the new TransactionBuilder
        // To destroy the old proposal state and replace with the new completion state.
        // Also add the Completed command with keys of all parties to signal the Tx purpose
        // to the Contract verify method.
        val tx = TransactionBuilder(notary).
                withItems(
                        latestRecord,
                        newState,
                        Command(TradeApprovalContract.Commands.Completed(),
                                listOf(serviceHub.myInfo.legalIdentity.owningKey, latestRecord.state.data.source.owningKey)))
        tx.setTimeWindow(serviceHub.clock.instant(), 60.seconds)
        // We can sign this transaction immediately as we have already checked all the fields and the decision
        // is ultimately a manual one from the caller.
        // As a SignedTransaction we can pass the data around certain that it cannot be modified,
        // although we do require further signatures to complete the process.
        val selfSignedTx = serviceHub.signInitialTransaction(tx)
        //DOCEND 2
        // Send the signed transaction to the originator and await their signature to confirm
        val allPartySignedTx = sendAndReceive<TransactionSignature>(newState.source, selfSignedTx).unwrap {
            // Add their signature to our unmodified transaction. To check they signed the same tx.
            val agreedTx = selfSignedTx + it
            // Receive back their signature and confirm that it is for an unmodified transaction
            // Also that the only missing signature is from teh Notary
            agreedTx.verifySignaturesExcept(notary.owningKey)
            // Recheck the data of the transaction. Note we run toLedgerTransaction on the WireTransaction
            // as we do not have all the signature.
            agreedTx.tx.toLedgerTransaction(serviceHub).verify()
            // return the SignedTransaction to notarise
            agreedTx
        }
        // DOCSTART 4
        // Notarise and distribute the completed transaction.
        subFlow(FinalityFlow(allPartySignedTx, setOf(latestRecord.state.data.source, latestRecord.state.data.counterparty)))
        // DOCEND 4
        // Return back the details of the completed state/transaction.
        return allPartySignedTx.tx.outRef<TradeApprovalContract.State>(0)
    }
}

/**
 * Simple flow to receive the final decision on a proposal.
 * Then after checking to sign it and eventually store the fully notarised
 * transaction to the ledger.
 */
@InitiatedBy(SubmitCompletionFlow::class)
class RecordCompletionFlow(val source: Party) : FlowLogic<Unit>() {
    @Suspendable
    override fun call(): Unit {
        // DOCSTART 3
        // First we receive the verdict transaction signed by their single key
        val completeTx = receive<SignedTransaction>(source).unwrap {
            // Check the transaction is signed apart from our own key and the notary
            it.verifySignaturesExcept(serviceHub.myInfo.legalIdentity.owningKey, it.tx.notary!!.owningKey)
            // Check the transaction data is correctly formed
            val ltx = it.toLedgerTransaction(serviceHub, false)
            ltx.verify()
            // Confirm that this is the expected type of transaction
            require(ltx.commands.single().value is TradeApprovalContract.Commands.Completed) {
                "Transaction must represent a workflow completion"
            }
            // Check the context dependent parts of the transaction as the
            // Contract verify method must not use serviceHub queries.
            val state = ltx.outRef<TradeApprovalContract.State>(0)
            require(state.state.data.source == serviceHub.myInfo.legalIdentity) {
                "Proposal not one of our original proposals"
            }
            require(state.state.data.counterparty == source) {
                "Proposal not for sent from correct source"
            }
            it
        }
        // DOCEND 3
        // Having verified the SignedTransaction passed to us we can sign it too
        val ourSignature = serviceHub.createSignature(completeTx)
        // Send our signature to the other party.
        send(source, ourSignature)
        // N.B. The FinalityProtocol will be responsible for Notarising the SignedTransaction
        // and broadcasting the result to us.
    }
}