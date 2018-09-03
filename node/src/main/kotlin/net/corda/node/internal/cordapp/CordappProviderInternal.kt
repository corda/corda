package net.corda.node.internal.cordapp

import net.corda.core.cordapp.Cordapp
import net.corda.core.cordapp.CordappProvider
import net.corda.core.flows.FlowLogic

interface CordappProviderInternal : CordappProvider {
    val cordapps: List<Cordapp>
    fun getCordappForFlow(flowLogic: FlowLogic<*>): Cordapp?
}
