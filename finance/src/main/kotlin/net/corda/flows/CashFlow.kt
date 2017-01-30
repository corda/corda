package net.corda.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Issued
import net.corda.core.crypto.Party
import net.corda.core.flows.FlowLogic
import net.corda.core.serialization.OpaqueBytes
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import java.util.*

/**
 * Initiates a flow that produces an Issue/Move or Exit Cash transaction.
 *
 * @param command Indicates what Cash transaction to create with what parameters.
 */
class CashFlow(val command: CashFlow.Command, override val progressTracker: ProgressTracker) : FlowLogic<SignedTransaction>() {
    constructor(command: CashFlow.Command) : this(command, tracker())

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
            is CashFlow.Command.IssueCash -> subFlow(CashIssueFlow(command.amount, command.issueRef, command.recipient, command.notary))
            is CashFlow.Command.PayCash -> subFlow(CashPaymentFlow(command.amount, command.recipient))
            is CashFlow.Command.ExitCash -> subFlow(CashExitFlow(command.amount, command.issueRef))
        }
    }

    /**
     * A command to initiate the Cash flow with.
     */
    sealed class Command {
        /**
         * A command to initiate the Cash flow with.
         */
        class IssueCash(val amount: Amount<Currency>,
                        val issueRef: OpaqueBytes,
                        val recipient: Party,
                        val notary: Party) : CashFlow.Command()

        /**
         * Pay cash to someone else.
         *
         * @param amount the amount of currency to issue on to the ledger.
         * @param recipient the party to issue the cash to.
         */
        class PayCash(val amount: Amount<Issued<Currency>>, val recipient: Party) : CashFlow.Command()

        /**
         * Exit cash from the ledger.
         *
         * @param amount the amount of currency to exit from the ledger.
         * @param issueRef the reference previously specified on the issuance.
         */
        class ExitCash(val amount: Amount<Currency>, val issueRef: OpaqueBytes) : CashFlow.Command()
    }
}
