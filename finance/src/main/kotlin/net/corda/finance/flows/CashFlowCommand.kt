package net.corda.finance.flows

import net.corda.core.contracts.Amount
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.FlowHandle
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import java.util.*

/**
 * A command to initiate the cash flow with.
 */
@Deprecated("Please use the flows directly, these will be removed in a later release")
sealed class CashFlowCommand {
    abstract fun startFlow(proxy: CordaRPCOps): FlowHandle<AbstractCashFlow.Result>

    /**
     * A command to initiate the Cash flow with.
     */
    data class IssueCash(val amount: Amount<Currency>,
                         val issueRef: OpaqueBytes,
                         val recipient: Party,
                         val notary: Party,
                         val anonymous: Boolean) : CashFlowCommand() {
        override fun startFlow(proxy: CordaRPCOps): FlowHandle<AbstractCashFlow.Result> {
            proxy.startFlow(::CashIssueFlow, amount, issueRef, notary)
            return proxy.startFlow(CashPaymentFlow::Initiate, amount, recipient, anonymous)
        }
    }

    /**
     * Pay cash to someone else.
     *
     * @param amount the amount of currency to issue on to the ledger.
     * @param recipient the party to issue the cash to.
     */
    data class PayCash(val amount: Amount<Currency>, val recipient: Party, val issuerConstraint: Party? = null,
                       val anonymous: Boolean) : CashFlowCommand() {
        override fun startFlow(proxy: CordaRPCOps) = proxy.startFlow(CashPaymentFlow::Initiate, amount, recipient, anonymous)
    }

    /**
     * Exit cash from the ledger.
     *
     * @param amount the amount of currency to exit from the ledger.
     * @param issueRef the reference previously specified on the issuance.
     */
    data class ExitCash(val amount: Amount<Currency>, val issueRef: OpaqueBytes) : CashFlowCommand() {
        override fun startFlow(proxy: CordaRPCOps) = proxy.startFlow(::CashExitFlow, amount, issueRef)
    }
}