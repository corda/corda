package net.corda.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.contracts.asset.Cash
import net.corda.core.contracts.*
import net.corda.core.crypto.AnonymousParty
import net.corda.core.crypto.Party
import net.corda.core.crypto.keys
import net.corda.core.crypto.toStringShort
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
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
class CashFlow(val command: CashCommand, override val progressTracker: ProgressTracker) : FlowLogic<SignedTransaction>() {
    constructor(command: CashCommand) : this(command, tracker())

    companion object {
        object ISSUING : ProgressTracker.Step("Issuing cash")
        object PAYING : ProgressTracker.Step("Paying cash")
        object EXITING : ProgressTracker.Step("Exiting cash")

        fun tracker() = ProgressTracker(ISSUING, PAYING, EXITING)
    }

    @Suspendable
    @Throws(CashException::class)
    override fun call(): SignedTransaction {
        return when (command) {
            is CashCommand.IssueCash -> issueCash(command)
            is CashCommand.PayCash -> initiatePayment(command)
            is CashCommand.ExitCash -> exitCash(command)
        }
    }

    // TODO check with the recipient if they want to accept the cash.
    @Suspendable
    private fun initiatePayment(req: CashCommand.PayCash): SignedTransaction {
        progressTracker.currentStep = PAYING
        val builder: TransactionBuilder = TransactionType.General.Builder(null)
        // TODO: Have some way of restricting this to states the caller controls
        val (spendTX, keysForSigning) = try {
            serviceHub.vaultService.generateSpend(
                    builder,
                    req.amount.withoutIssuer(),
                    req.recipient.owningKey,
                    setOf(req.amount.token.issuer.party))
        } catch (e: InsufficientBalanceException) {
            throw CashException("Insufficent cash for spend", e)
        }

        keysForSigning.keys.forEach {
            val key = serviceHub.keyManagementService.keys[it] ?: throw IllegalStateException("Could not find signing key for ${it.toStringShort()}")
            builder.signWith(KeyPair(it, key))
        }

        val tx = spendTX.toSignedTransaction(checkSufficientSignatures = false)
        finaliseTx(setOf(req.recipient), tx, "Unable to notarise spend")
        return tx
    }

    @Suspendable
    private fun exitCash(req: CashCommand.ExitCash): SignedTransaction {
        progressTracker.currentStep = EXITING
        val builder: TransactionBuilder = TransactionType.General.Builder(null)
        val issuer = serviceHub.myInfo.legalIdentity.ref(req.issueRef)
        try {
            Cash().generateExit(
                    builder,
                    req.amount.issuedBy(issuer),
                    serviceHub.vaultService.currentVault.statesOfType<Cash.State>().filter { it.state.data.owner == issuer.party.owningKey })
        } catch (e: InsufficientBalanceException) {
            throw CashException("Exiting more cash than exists", e)
        }
        val myKey = serviceHub.legalIdentityKey
        builder.signWith(myKey)

        // Work out who the owners of the burnt states were
        val inputStatesNullable = serviceHub.vaultService.statesForRefs(builder.inputStates())
        val inputStates = inputStatesNullable.values.filterNotNull().map { it.data }
        if (inputStatesNullable.size != inputStates.size) {
            val unresolvedStateRefs = inputStatesNullable.filter { it.value == null }.map { it.key }
            throw IllegalStateException("Failed to resolve input StateRefs: $unresolvedStateRefs")
        }

        // TODO: Is it safe to drop participants we don't know how to contact? Does not knowing how to contact them
        //       count as a reason to fail?
        val participants: Set<Party> = inputStates
                .filterIsInstance<Cash.State>()
                .map { serviceHub.identityService.partyFromKey(it.owner) }
                .filterNotNull()
                .toSet()

        // Commit the transaction
        val tx = builder.toSignedTransaction(checkSufficientSignatures = false)
        finaliseTx(participants, tx, "Unable to notarise exit")
        return tx
    }

    @Suspendable
    private fun finaliseTx(participants: Set<Party>, tx: SignedTransaction, message: String) {
        try {
            subFlow(FinalityFlow(tx, participants))
        } catch (e: NotaryException) {
            throw CashException(message, e)
        }
    }

    // TODO This doesn't throw any exception so it might be worth splitting the three cash commands into separate flows
    @Suspendable
    private fun issueCash(req: CashCommand.IssueCash): SignedTransaction {
        progressTracker.currentStep = ISSUING
        val builder: TransactionBuilder = TransactionType.General.Builder(notary = null)
        val issuer = serviceHub.myInfo.legalIdentity.ref(req.issueRef)
        Cash().generateIssue(builder, req.amount.issuedBy(issuer), req.recipient.owningKey, req.notary)
        val myKey = serviceHub.legalIdentityKey
        builder.signWith(myKey)
        val tx = builder.toSignedTransaction()
        subFlow(FinalityFlow(tx))
        return tx
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

class CashException(message: String, cause: Throwable) : FlowException(message, cause)
