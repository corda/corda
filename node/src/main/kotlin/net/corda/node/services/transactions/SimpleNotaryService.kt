package net.corda.node.services.transactions

import net.corda.core.flows.FlowSession
import net.corda.core.internal.notary.NotaryServiceFlow
import net.corda.core.internal.notary.TrustedAuthorityNotaryService
import net.corda.node.services.api.ServiceHubInternal
import java.security.PublicKey

/** A simple Notary service that does not perform transaction validation */
class SimpleNotaryService(override val services: ServiceHubInternal, override val notaryIdentityKey: PublicKey) : TrustedAuthorityNotaryService() {
    override val uniquenessProvider = PersistentUniquenessProvider(services.clock)

    override fun createServiceFlow(otherPartySession: FlowSession): NotaryServiceFlow = NonValidatingNotaryFlow(otherPartySession, this)

    override fun start() {}
    override fun stop() {}
}