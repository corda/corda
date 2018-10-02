package net.corda.node.internal

import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession

sealed class InitiatedFlowFactory<out F : FlowLogic<*>> {
    protected abstract val factory: (FlowSession) -> F
    fun createFlow(initiatingFlowSession: FlowSession): F = factory(initiatingFlowSession)

    data class Core<out F : FlowLogic<*>>(override val factory: (FlowSession) -> F) : InitiatedFlowFactory<F>()
    data class CorDapp<out F : FlowLogic<*>>(val flowVersion: Int,
                                             val appName: String,
                                             override val factory: (FlowSession) -> F) : InitiatedFlowFactory<F>()
}
