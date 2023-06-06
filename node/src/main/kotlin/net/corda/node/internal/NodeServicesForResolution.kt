package net.corda.node.internal

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionResolutionException
import net.corda.core.node.ServicesForResolution
import java.util.LinkedHashSet

interface NodeServicesForResolution : ServicesForResolution {
    @Throws(TransactionResolutionException::class)
    override fun loadStates(stateRefs: Set<StateRef>): Set<StateAndRef<ContractState>> = loadStates(stateRefs, LinkedHashSet())

    fun <T : ContractState, C : MutableCollection<StateAndRef<T>>> loadStates(input: Iterable<StateRef>, output: C): C
}
