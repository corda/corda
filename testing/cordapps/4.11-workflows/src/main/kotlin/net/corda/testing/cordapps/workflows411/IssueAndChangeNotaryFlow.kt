package net.corda.testing.cordapps.workflows411

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.NotaryChangeFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.NotaryChangeWireTransaction
import net.corda.core.utilities.OpaqueBytes
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashIssueFlow

// We need a separate flow as NotaryChangeFlow is not StartableByRPC
@StartableByRPC
class IssueAndChangeNotaryFlow(private val oldNotary: Party, private val newNotary: Party) : FlowLogic<SecureHash>() {
    @Suppress("MagicNumber")
    @Suspendable
    override fun call(): SecureHash {
        subFlow(CashIssueFlow(10.DOLLARS, OpaqueBytes.of(0x01), oldNotary))
        val oldState = serviceHub.vaultService.queryBy(Cash.State::class.java).states.single()
        check(oldState.state.notary == oldNotary) { oldState.state.notary }
        val newState = subFlow(NotaryChangeFlow(oldState, newNotary))
        check(newState.state.notary == newNotary) { newState.state.notary }
        val notaryChangeTx = checkNotNull(serviceHub.validatedTransactions.getTransaction(newState.ref.txhash))
        check(notaryChangeTx.coreTransaction is NotaryChangeWireTransaction) { notaryChangeTx.coreTransaction }
        return notaryChangeTx.id
    }
}
