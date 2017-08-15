package net.corda.node.services.transactions

import net.corda.core.flows.NotaryFlow
import net.corda.core.identity.Party
import net.corda.core.node.services.TimeWindowChecker
import net.corda.core.node.services.TrustedAuthorityNotaryService
import net.corda.node.services.api.ServiceHubInternal

/** A non-validating notary service operated by a group of mutually trusting parties, uses the Raft algorithm to achieve consensus. */
class RaftNonValidatingNotaryService(override val services: ServiceHubInternal) : TrustedAuthorityNotaryService() {
    companion object {
        val type = SimpleNotaryService.type.getSubType("raft")
    }

    override val timeWindowChecker: TimeWindowChecker = TimeWindowChecker(services.clock)
    override val uniquenessProvider: RaftUniquenessProvider = RaftUniquenessProvider(services)

    override fun createServiceFlow(otherParty: Party): NotaryFlow.Service = NonValidatingNotaryFlow(otherParty, this)

    override fun start() {
        uniquenessProvider.start()
    }

    override fun stop() {
        uniquenessProvider.stop()
    }
}