package net.corda.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.contracts.asset.Cash
import net.corda.core.contracts.*
import net.corda.core.crypto.Party
import net.corda.core.crypto.keys
import net.corda.core.crypto.toStringShort
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StateMachineRunId
import net.corda.core.serialization.OpaqueBytes
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.security.KeyPair
import java.util.*

/**
 * Initiates a flow that produces an Issue/Move or Exit Cash transaction.
 *
 * @param command Indicates what Cash transaction to create with what parameters.
 */
class CashFlow(val command: CashCommand, override val progressTracker: ProgressTracker) : FlowLogic<CashFlowResult>() {
    constructor(command: CashCommand) : this(command, tracker())

    companion object {
        object ISSUING : ProgressTracker.Step("Issuing cash")
        object PAYING : ProgressTracker.Step("Paying cash")
        object EXITING : ProgressTracker.Step("Exiting cash")

        fun tracker() = ProgressTracker(ISSUING, PAYING, EXITING)
    }

    @Suspendable
    override fun call(): CashFlowResult {
        return when (command) {
            is CashCommand.IssueCash -> issueCash(command)
            is CashCommand.PayCash -> initiatePayment(command)
            is CashCommand.ExitCash -> exitCash(command)
        }
    }

    // TODO check with the recipient if they want to accept the cash.
    @Suspendable
    private fun initiatePayment(req: CashCommand.PayCash): CashFlowResult {
        progressTracker.currentStep = PAYING
        val builder: TransactionBuilder = TransactionType.General.Builder(null)
        // TODO: Have some way of restricting this to states the caller controls
        try {
            val (spendTX, keysForSigning) = serviceHub.vaultService.generateSpend(builder,
                    req.amount.withoutIssuer(), req.recipient.owningKey, setOf(req.amount.token.issuer.party))

            keysForSigning.keys.forEach {
                val key = serviceHub.keyManagementService.keys[it] ?: throw IllegalStateException("Could not find signing key for ${it.toStringShort()}")
                builder.signWith(KeyPair(it, key))
            }

            val tx = spendTX.toSignedTransaction(checkSufficientSignatures = false)
            val flow = FinalityFlow(tx, setOf(req.recipient))
            subFlow(flow)
            return CashFlowResult.Success(
                    stateMachine.id,
                    tx,
                    "Cash payment transaction generated"
            )
        } catch(ex: InsufficientBalanceException) {
            return CashFlowResult.Failed(ex.message ?: "Insufficient balance")
        }
    }

    @Suspendable
    private fun exitCash(req: CashCommand.ExitCash): CashFlowResult {
        progressTracker.currentStep = EXITING
        val builder: TransactionBuilder = TransactionType.General.Builder(null)
        try {
            val issuer = PartyAndReference(serviceHub.myInfo.legalIdentity, req.issueRef)
            Cash().generateExit(builder, req.amount.issuedBy(issuer),
                    serviceHub.vaultService.currentVault.statesOfType<Cash.State>().filter { it.state.data.owner == issuer.party.owningKey })
            val myKey = serviceHub.legalIdentityKey
            builder.signWith(myKey)

            // Work out who the owners of the burnt states were
            val inputStatesNullable = serviceHub.vaultService.statesForRefs(builder.inputStates())
            val inputStates = inputStatesNullable.values.filterNotNull().map { it.data }
            if (inputStatesNullable.size != inputStates.size) {
                val unresolvedStateRefs = inputStatesNullable.filter { it.value == null }.map { it.key }
                throw InputStateRefResolveFailed(unresolvedStateRefs)
            }

            // TODO: Is it safe to drop participants we don't know how to contact? Does not knowing how to contact them
            //       count as a reason to fail?
            val participants: Set<Party> = inputStates.filterIsInstance<Cash.State>().map { serviceHub.identityService.partyFromKey(it.owner) }.filterNotNull().toSet()

            // Commit the transaction
            val tx = builder.toSignedTransaction(checkSufficientSignatures = false)
            subFlow(FinalityFlow(tx, participants))
            return CashFlowResult.Success(
                    stateMachine.id,
                    tx,
                    "Cash destruction transaction generated"
            )
        } catch (ex: InsufficientBalanceException) {
            return CashFlowResult.Failed(ex.message ?: "Insufficient balance")
        }
    }

    @Suspendable
    private fun issueCash(req: CashCommand.IssueCash): CashFlowResult {
        progressTracker.currentStep = ISSUING
        val builder: TransactionBuilder = TransactionType.General.Builder(notary = null)
        val issuer = PartyAndReference(serviceHub.myInfo.legalIdentity, req.issueRef)
        Cash().generateIssue(builder, req.amount.issuedBy(issuer), req.recipient.owningKey, req.notary)
        val myKey = serviceHub.legalIdentityKey
        builder.signWith(myKey)
        val tx = builder.toSignedTransaction(checkSufficientSignatures = true)
        // Issuance transactions do not need to be notarised, so we can skip directly to broadcasting it
        subFlow(BroadcastTransactionFlow(tx, setOf(req.recipient)))
        return CashFlowResult.Success(
                stateMachine.id,
                tx,
                "Cash issuance completed"
        )
    }


}

/**
 * A command to initiate the Cash flow with.
 */
sealed class CashCommand {
    /**
     * Issue cash state objects.
     *
     * @param amount the amount of currency to issue on to the ledger.
     * @param issueRef the reference to specify on the issuance, used to differentiate pools of cash. Convention is
     * to use the single byte "0x01" as a default.
     * @param recipient the party to issue the cash to.
     * @param notary the notary to use for this transaction.
     */
    class IssueCash(val amount: Amount<Currency>,
                    val issueRef: OpaqueBytes,
                    val recipient: Party,
                    val notary: Party) : CashCommand()

    /**
     * Pay cash to someone else.
     *
     * @param amount the amount of currency to issue on to the ledger.
     * @param recipient the party to issue the cash to.
     */
    class PayCash(val amount: Amount<Issued<Currency>>, val recipient: Party) : CashCommand()

    /**
     * Exit cash from the ledger.
     *
     * @param amount the amount of currency to exit from the ledger.
     * @param issueRef the reference previously specified on the issuance.
     */
    class ExitCash(val amount: Amount<Currency>, val issueRef: OpaqueBytes) : CashCommand()
}

sealed class CashFlowResult {
    /**
     * @param transaction the transaction created as a result, in the case where the flow completed successfully.
     */
    class Success(val id: StateMachineRunId, val transaction: SignedTransaction?, val message: String?) : CashFlowResult() {
        override fun toString() = "Success($message)"
    }

    /**
     * State indicating the action undertaken failed, either directly (it is not something which requires a
     * state machine), or before a state machine was started.
     */
    class Failed(val message: String?) : CashFlowResult() {
        override fun toString() = "Failed($message)"
    }
}

class InputStateRefResolveFailed(stateRefs: List<StateRef>) :
        Exception("Failed to resolve input StateRefs $stateRefs")
