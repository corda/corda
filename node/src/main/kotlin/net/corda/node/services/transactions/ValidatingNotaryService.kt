package net.corda.node.services.transactions

import net.corda.core.flows.FlowSession
import net.corda.core.internal.notary.NotaryServiceFlow
import net.corda.core.internal.notary.TrustedAuthorityNotaryService
import net.corda.node.services.api.ServiceHubInternal
import java.security.PublicKey

/** A Notary service that validates the transaction chain of the submitted transaction before committing it */
class ValidatingNotaryService(override val services: ServiceHubInternal, override val notaryIdentityKey: PublicKey) : TrustedAuthorityNotaryService() {
    override val uniquenessProvider = PersistentUniquenessProvider(services.clock)

    override fun createServiceFlow(otherPartySession: FlowSession): NotaryServiceFlow = ValidatingNotaryFlow(otherPartySession, this)

    override fun start() {}
    override fun stop() {}
}