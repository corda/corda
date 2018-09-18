package net.corda.flows.overrides

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap

@InitiatingFlow
@StartableByRPC
class Initiator : FlowLogic<List<String>>() {


    companion object {
        val asking = ProgressTracker.Step("asking")
        val asked = ProgressTracker.Step("asked")

    }

    override val progressTracker: ProgressTracker
        get() = ProgressTracker(asking, asked)

    @Suspendable
    override fun call(): List<String> {
        progressTracker.currentStep = asking
        return serviceHub.networkMapCache.allNodes.filter { it.legalIdentities.first() != ourIdentity }.map { it.legalIdentities.first() }.map { le ->
            val session = initiateFlow(le)
            le.name.organisation + " replied with " + session.receive<String>().unwrap { it }
        }.also {
            progressTracker.currentStep = asked
        }
    }
}