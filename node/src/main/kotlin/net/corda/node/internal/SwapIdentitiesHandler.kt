package net.corda.node.internal

import co.paralleluniverse.fibers.Suspendable
import net.corda.confidential.SwapIdentitiesFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.internal.warnOnce

/**
 * Exists for backwards compatibility - the old confidential-identities API didn't use inlined flows. One day we'll need to disable this,
 * but it is a complex versioning problem because we don't know which peers we might interact with. Disabling it will probably have to be
 * gated on a minPlatformVersion bump.
 */
//@InitiatedBy(SwapIdentitiesFlow::class)
class SwapIdentitiesHandler(private val otherSide: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        subFlow(SwapIdentitiesFlow(otherSide))
        logger.warnOnce("Insecure API to swap anonymous identities was used by ${otherSide.counterparty} (${otherSide.getCounterpartyFlowInfo()})")
    }
}
