package net.corda.flows

import net.corda.core.contracts.Amount
import net.corda.core.contracts.Issued
import net.corda.core.crypto.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.FlowHandle
import net.corda.core.messaging.startFlow
import net.corda.core.serialization.OpaqueBytes
import net.corda.core.transactions.SignedTransaction
import java.util.*

/**
 * A command to initiate the Cash flow with.
 */
sealed class CashFlowCommand {
    abstract fun startFlow(proxy: CordaRPCOps): FlowHandle<SignedTransaction>

    /**
     * A command to initiate the Cash flow with.
     */
    class IssueCash(val amount: Amount<Currency>,
                    val issueRef: OpaqueBytes,
                    val recipient: Party,
                    val notary: Party) : CashFlowCommand() {
        override fun startFlow(proxy: CordaRPCOps) = proxy.startFlow(::CashIssueFlow, amount, issueRef, recipient, notary)
    }

    /**
     * Pay cash to someone else.
     *
     * @param amount the amount of currency to issue on to the ledger.
     * @param recipient the party to issue the cash to.
     */
    class PayCash(val amount: Amount<Issued<Currency>>, val recipient: Party) : CashFlowCommand() {
        override fun startFlow(proxy: CordaRPCOps) = proxy.startFlow(::CashPaymentFlow, amount, recipient)
    }

    /**
     * Exit cash from the ledger.
     *
     * @param amount the amount of currency to exit from the ledger.
     * @param issueRef the reference previously specified on the issuance.
     */
    class ExitCash(val amount: Amount<Currency>, val issueRef: OpaqueBytes) : CashFlowCommand() {
        override fun startFlow(proxy: CordaRPCOps) = proxy.startFlow(::CashExitFlow, amount, issueRef)
    }
}