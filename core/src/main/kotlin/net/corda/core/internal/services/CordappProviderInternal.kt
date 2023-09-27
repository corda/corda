package net.corda.core.internal.services

import net.corda.core.cordapp.Cordapp
import net.corda.core.cordapp.CordappProvider
import net.corda.core.flows.FlowLogic
import net.corda.core.internal.cordapp.CordappImpl
import net.corda.core.internal.cordapp.CordappLoader

interface CordappProviderInternal : CordappProvider {
    val cordappLoader: CordappLoader
    val cordapps: List<CordappImpl>
    fun getCordappForFlow(flowLogic: FlowLogic<*>): Cordapp?
}
