package net.corda.isolated.workflows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateRef
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.isolated.contracts.AnotherDummyContract

@StartableByRPC
class IsolatedIssuanceFlow(private val magicNumber: Int) : FlowLogic<StateRef>() {
    @Suspendable
    override fun call(): StateRef {
        val stx = serviceHub.signInitialTransaction(
                AnotherDummyContract().generateInitial(
                        ourIdentity.ref(0),
                        magicNumber,
                        serviceHub.networkMapCache.notaryIdentities.first()
                )
        )
        stx.verify(serviceHub)
        serviceHub.recordTransactions(stx)
        return stx.tx.outRef<ContractState>(0).ref
    }
}
