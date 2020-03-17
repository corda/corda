package net.corda.flows.serialization.whitelist

import co.paralleluniverse.fibers.Suspendable
import net.corda.contracts.serialization.whitelist.WhitelistContract
import net.corda.contracts.serialization.whitelist.WhitelistContract.State
import net.corda.contracts.serialization.whitelist.WhitelistData
import net.corda.core.contracts.Command
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.TransactionBuilder

@StartableByRPC
class WhitelistFlow(private val data: WhitelistData) : FlowLogic<SecureHash>() {
    @Suspendable
    override fun call(): SecureHash {
        val notary = serviceHub.networkMapCache.notaryIdentities[0]
        val stx = serviceHub.signInitialTransaction(
            TransactionBuilder(notary)
                .addOutputState(State(ourIdentity, data))
                .addCommand(Command(WhitelistContract.Operate(), ourIdentity.owningKey))
        )
        stx.verify(serviceHub, checkSufficientSignatures = false)
        return stx.id
    }
}
