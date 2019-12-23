package net.corda.flows.djvm.whitelist

import co.paralleluniverse.fibers.Suspendable
import net.corda.contracts.djvm.whitelist.DeterministicWhitelistContract.Operate
import net.corda.contracts.djvm.whitelist.DeterministicWhitelistContract.State
import net.corda.contracts.djvm.whitelist.WhitelistData
import net.corda.core.contracts.Command
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.TransactionBuilder

@InitiatingFlow
@StartableByRPC
class DeterministicWhitelistFlow(private val data: WhitelistData) : FlowLogic<SecureHash>() {
    @Suspendable
    override fun call(): SecureHash {
        val notary = serviceHub.networkMapCache.notaryIdentities[0]
        val stx = serviceHub.signInitialTransaction(
            TransactionBuilder(notary)
                .addOutputState(State(ourIdentity, data))
                .addCommand(Command(Operate(), ourIdentity.owningKey))
        )
        stx.verify(serviceHub, checkSufficientSignatures = false)
        return stx.id
    }
}
