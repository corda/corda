package net.corda.node.internal

import net.corda.core.flows.type.FlowLogic
import net.corda.core.identity.Party

sealed class InitiatedFlowFactory<out F : FlowLogic<*>> {
    protected abstract val factory: (Party) -> F
    fun createFlow(otherParty: Party): F = factory(otherParty)

    data class Core<out F : FlowLogic<*>>(override val factory: (Party) -> F) : InitiatedFlowFactory<F>()
    data class CorDapp<out F : FlowLogic<*>>(val flowVersion: Int,
                                             val appName: String,
                                             override val factory: (Party) -> F) : InitiatedFlowFactory<F>()
}

