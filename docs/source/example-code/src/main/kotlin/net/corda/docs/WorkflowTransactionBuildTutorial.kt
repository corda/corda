package net.corda.docs

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.*
import net.corda.core.crypto.*
import net.corda.core.flows.FlowLogic
import net.corda.core.node.PluginServiceHub
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.linearHeadsOfType
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.unwrap
import net.corda.flows.FinalityFlow
import java.security.PublicKey
import java.time.Duration

object WorkflowTransactionBuildTutorial {
    // Would normally be called by custom service init in a CorDapp
    fun registerWorkflowProtocols(pluginHub: PluginServiceHub) {
        pluginHub.registerFlowInitiator(SubmitCompletionFlow::class, ::RecordCompletionFlow)
    }
}

// DOCSTART 1

// Helper method to locate the latest Vault version of a LinearState from a possibly out of date StateRef
inline fun <reified T : LinearState> ServiceHub.latest(ref: StateRef): StateAndRef<T> {
    val linearHeads = vaultService.linearHeadsOfType<T>()
    val original = toStateAndRef<T>(ref)
    return linearHeads.get(original.state.data.linearId)!!
}

// DOCEND 1

// Minimal state model of a manual approval process
enum class WorkflowState {
    NEW,
    APPROVED,
    REJECTED
}

/**
 * Minimal contract to encode a simple workflow with one initial state and two possible eventual states.
 * It is assumed one party unilaterally submits and the other manually retrieves the deal and completes it.
 */
data class TradeApprovalContract(override val legalContractReference: SecureHash = SecureHash.sha256("Example of workflow type transaction")) : Contract {

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
        override val participants: List<CompositeKey> get() = parties.map { it.owningKey }

        override fun isRelevant(ourKeys: Set<PublicKey>): Boolean {
            return participants.any { it.containsAny(ourKeys) }
        }
    }

    /**
     * The verify method locks down the allowed transactions to contain just a single proposal being
     * created/modified and the only modification allowed is to the state field.
     */
    override fun verify(tx: TransactionForContract) {
        val command = tx.commands.requireSingleCommand<TradeApprovalContract.Commands>()
        require(tx.timestamp?.midpoint != null) { "must be timestamped" }
        when (command.value) {
            is Commands.Issue -> {
                requireThat {
                    "Issue of new WorkflowContract must not include any inputs" by (tx.inputs.isEmpty())
                    "Issue of new WorkflowContract must be in a unique transaction" by (tx.outputs.size == 1)
                }
                val issued = tx.outputs.get(0) as TradeApprovalContract.State
                requireThat {
                    "Issue requires the source Party as signer" by (command.signers.contains(issued.source.owningKey))
                    "Initial Issue state must be NEW" by (issued.state == WorkflowState.NEW)
                }
            }
            is Commands.Completed -> {
                val stateGroups = tx.groupStates(TradeApprovalContract.State::class.java) { it.linearId }
                require(stateGroups.size == 1) { "Must be only a single proposal in transaction" }
                for (group in stateGroups) {
                    val before = group.inputs.single()
                    val after = group.outputs.single()
                    requireThat {
                        "Only a non-final trade can be modified" by (before.state == WorkflowState.NEW)
                        "Output must be a final state" by (after.state in setOf(WorkflowState.APPROVED, WorkflowState.REJECTED))
                        "Completed command can only change state" by (before == after.copy(state = before.state))
                        "Completed command requires the source Party as signer" by (command.signers.contains(before.source.owningKey))
                        "Completed command requires the counterparty as signer" by (command.signers.contains(before.counterparty.owningKey))
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
        val tx = TransactionType.General.Builder(notary)
                .withItems(tradeProposal, Command(TradeApprovalContract.Commands.Issue(), listOf(tradeProposal.source.owningKey)))
        tx.setTime(serviceHub.clock.instant(), Duration.ofSeconds(60))
        // We can automatically sign as there is no untrusted data.
        tx.signWith(serviceHub.legalIdentityKey)
        // Convert to a SignedTransaction that we can send to the notary
        val signedTx = tx.toSignedTransaction(false)
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
class SubmitCompletionFlow(val ref: StateRef, val verdict: WorkflowState) : FlowLogic<StateAndRef<TradeApprovalContract.State>>() {
    init {
        require(verdict in setOf(WorkflowState.APPROVED, WorkflowState.REJECTED)) {
            "Verdict must be a final state"
        }
    }

    @Suspendable
    override fun call(): StateAndRef<TradeApprovalContract.State> {
        // Pull in the latest Vault version of the StateRef as a full StateAndRef
        val latestRecord = serviceHub.latest<TradeApprovalContract.State>(ref)
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
        val tx = TransactionType.
                General.
                Builder(notary).
                withItems(
                        latestRecord,
                        newState,
                        Command(TradeApprovalContract.Commands.Completed(),
                                listOf(serviceHub.myInfo.legalIdentity.owningKey, latestRecord.state.data.source.owningKey)))
        tx.setTime(serviceHub.clock.instant(), Duration.ofSeconds(60))
        // We can sign this transaction immediately as we have already checked all the fields and the decision
        // is ultimately a manual one from the caller.
        tx.signWith(serviceHub.legalIdentityKey)
        // Convert to SignedTransaction we can pass around certain that it cannot be modified.
        val selfSignedTx = tx.toSignedTransaction(false)
        //DOCEND 2
        // Send the signed transaction to the originator and await their signature to confirm
        val allPartySignedTx = sendAndReceive<DigitalSignature.WithKey>(newState.source, selfSignedTx).unwrap {
            // Add their signature to our unmodified transaction. To check they signed the same tx.
            val agreedTx = selfSignedTx + it
            // Receive back their signature and confirm that it is for an unmodified transaction
            // Also that the only missing signature is from teh Notary
            agreedTx.verifySignatures(notary.owningKey)
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
class RecordCompletionFlow(val source: Party) : FlowLogic<Unit>() {
    @Suspendable
    override fun call(): Unit {
        // DOCSTART 3
        // First we receive the verdict transaction signed by their single key
        val completeTx = receive<SignedTransaction>(source).unwrap {
            // Check the transaction is signed apart from our own key and the notary
            val wtx = it.verifySignatures(serviceHub.myInfo.legalIdentity.owningKey, it.tx.notary!!.owningKey)
            // Check the transaction data is correctly formed
            wtx.toLedgerTransaction(serviceHub).verify()
            // Confirm that this is the expected type of transaction
            require(wtx.commands.single().value is TradeApprovalContract.Commands.Completed) {
                "Transaction must represent a workflow completion"
            }
            // Check the context dependent parts of the transaction as the
            // Contract verify method must not use serviceHub queries.
            val state = wtx.outRef<TradeApprovalContract.State>(0)
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
        val ourSignature = serviceHub.legalIdentityKey.signWithECDSA(completeTx.tx.id)
        // Send our signature to the other party.
        send(source, ourSignature)
        // N.B. The FinalityProtocol will be responsible for Notarising the SignedTransaction
        // and broadcasting the result to us.
    }
}