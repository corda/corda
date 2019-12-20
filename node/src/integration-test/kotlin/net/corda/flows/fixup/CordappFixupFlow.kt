package net.corda.flows.fixup

import co.paralleluniverse.fibers.Suspendable
import net.corda.contracts.fixup.dependent.DependentContract
import net.corda.contracts.fixup.dependent.DependentData
import net.corda.core.contracts.Command
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.TransactionBuilder

@InitiatingFlow
@StartableByRPC
class CordappFixupFlow(private val data: DependentData) : FlowLogic<SecureHash>() {
    @Suspendable
    override fun call(): SecureHash {
        val notary = serviceHub.networkMapCache.notaryIdentities[0]
        val stx = serviceHub.signInitialTransaction(
            TransactionBuilder(notary)
                .addOutputState(DependentContract.State(ourIdentity, data))
                .addCommand(Command(DependentContract.Operate(), ourIdentity.owningKey))
        )
        stx.verify(serviceHub, checkSufficientSignatures = false)
        return stx.id
    }
}