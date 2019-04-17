package net.corda.confidential.flow

import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.utilities.ProgressTracker

@InitiatingFlow
class RequestKeyFlow
    private constructor(private val otherSideSession: FlowSession?,
    private val otherParty: Party?,
    override val progressTracker: ProgressTracker) : FlowLogic<Unit>() {


    override fun call() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}